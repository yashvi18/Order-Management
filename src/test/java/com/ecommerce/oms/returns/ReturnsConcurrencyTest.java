package com.ecommerce.oms.returns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.order.OrderRepository;
import com.ecommerce.oms.payment.PaymentGateway;
import com.ecommerce.oms.payment.PaymentService;
import com.ecommerce.oms.returns.ReturnDtos.CreateReturnRequest;
import com.ecommerce.oms.returns.ReturnDtos.ReturnLine;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.util.ArrayList;
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
 * A {@code @MockitoSpyBean} field gives this class its own Spring context (separate from other integration
 * tests), which is expected and fine.
 */
class ReturnsConcurrencyTest extends IntegrationTestBase {

    @Autowired private ReturnService returnService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentService paymentService;
    @MockitoSpyBean private PaymentGateway gateway;

    private User alice;
    private User staffW1;
    private Product pen;

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        pen = fixtures.product(fixtures.category("Stationery", "0.05"), "PEN-1", "Pen", "3.33");
        Warehouse w1 = fixtures.warehouse("W1");
        fixtures.stock(pen, w1, 10, 0);
        staffW1 = fixtures.staff("staff1@test.local", w1);
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

    @Test
    void concurrentReceivesRefundExactlyOnce() throws Exception {
        fixtures.cartWith(alice, pen, 3);
        long orderId = checkout(alice);
        advanceToDelivered(orderId);
        long itemId = tx.execute(s -> orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getId());

        AppUserDetails aliceDetails = AppUserDetails.from(alice);
        long returnId = returnService
                .requestReturn(orderId, aliceDetails, new CreateReturnRequest(List.of(new ReturnLine(itemId, 1)), "Not needed"))
                .id();

        AppUserDetails staffDetails = AppUserDetails.from(staffW1);
        Callable<Boolean> receiveTask = () -> {
            try {
                returnService.receive(returnId, staffDetails, null);
                return true;
            } catch (ConflictException | ObjectOptimisticLockingFailureException e) {
                return false;
            }
        };

        int successes = runConcurrently(List.of(receiveTask, receiveTask));

        assertThat(successes).isEqualTo(1);
        assertThat(paymentService.findSummary(orderId).orElseThrow().refundedAmount()).isEqualByComparingTo("3.50");
        verify(gateway, times(1)).refund(anyString(), any());
    }
}
