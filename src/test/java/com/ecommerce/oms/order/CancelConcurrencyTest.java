package com.ecommerce.oms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.inventory.InventoryItemRepository;
import com.ecommerce.oms.payment.PaymentGateway;
import com.ecommerce.oms.payment.PaymentService;
import com.ecommerce.oms.payment.PaymentStatus;
import com.ecommerce.oms.payment.PaymentSummary;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Cancel calls the external gateway inside its transaction, so it must hold the order row lock while it checks the
 * status: otherwise the router or a warehouse update can commit in between and the refund has already left the PSP.
 * The spy slows the gateway refund down to make that window wide. Its own Spring context, like ReturnsConcurrencyTest.
 */
class CancelConcurrencyTest extends IntegrationTestBase {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private InventoryItemRepository inventoryRepository;
    @MockitoSpyBean private PaymentGateway gateway;

    private User alice;
    private AppUserDetails alicePrincipal;
    private AppUserDetails adminPrincipal;
    private final List<Exception> losers = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        alicePrincipal = AppUserDetails.from(alice);
        adminPrincipal = AppUserDetails.from(fixtures.admin());
        Product pen = fixtures.product(fixtures.category("Stationery", "0.05"), "PEN-1", "Pen", "10.00");
        fixtures.stock(pen, fixtures.warehouse("W1"), 10, 0);
        fixtures.cartWith(alice, pen, 2);
        doAnswer(invocation -> {
            Thread.sleep(300);
            return invocation.callRealMethod();
        }).when(gateway).refund(anyString(), any());
    }

    /** Runs all tasks at the same instant; returns how many returned true. Unexpected exceptions fail the test. */
    private int runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(60, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();
        return successes;
    }

    private Callable<Boolean> cancelTask(long orderId) {
        return () -> {
            try {
                orderService.cancel(orderId, alicePrincipal, null);
                return true;
            } catch (ConflictException | ObjectOptimisticLockingFailureException e) {
                losers.add(e);
                return false;
            }
        };
    }

    private OrderStatus statusOf(long orderId) {
        return tx.execute(s -> orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    private int reserved() {
        return inventoryRepository.findAll().getFirst().getReserved();
    }

    /** Either the cancel fully happened (DB and PSP agree) or it did not happen at all. */
    private void assertConsistent(long orderId, OrderStatus otherOutcome) {
        OrderStatus status = statusOf(orderId);
        PaymentSummary payment = paymentService.findSummary(orderId).orElseThrow();
        if (status == OrderStatus.CANCELLED) {
            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
            verify(gateway, times(1)).refund(anyString(), any());
            assertThat(reserved()).isZero();
        } else {
            assertThat(status).isEqualTo(otherOutcome);
            assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(payment.refundedAmount()).isEqualByComparingTo("0.00");
            verify(gateway, never()).refund(anyString(), any());
            assertThat(reserved()).isEqualTo(2);
        }
    }

    @Test
    void cancelRacingPackedEitherRefundsOrShipsNeverBoth() throws Exception {
        long orderId = checkout(alice);
        outboxProcessor.processBatch();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
        Callable<Boolean> pack = () -> {
            try {
                fulfillmentService.updateStatus(orderId, OrderStatus.PACKED, adminPrincipal, null);
                return true;
            } catch (ConflictException | ObjectOptimisticLockingFailureException e) {
                losers.add(e);
                return false;
            }
        };

        int successes = runConcurrently(List.of(cancelTask(orderId), pack));

        assertConsistent(orderId, OrderStatus.PACKED);
        assertThat(successes).isEqualTo(1);
        assertThat(losers).singleElement().isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelRacingRouterNeverRefundsAConfirmedOrder() throws Exception {
        long orderId = checkout(alice);
        Callable<Boolean> route = () -> {
            outboxProcessor.processBatch();
            return true;
        };

        runConcurrently(List.of(cancelTask(orderId), route));

        assertConsistent(orderId, OrderStatus.CONFIRMED);
        // CONFIRMED is still cancellable, so with the row lock the cancel always goes through whichever runs first.
        assertThat(losers).isEmpty();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELLED);
    }
}
