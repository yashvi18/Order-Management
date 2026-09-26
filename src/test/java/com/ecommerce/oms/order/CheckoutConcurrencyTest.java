package com.ecommerce.oms.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.inventory.InsufficientStockException;
import com.ecommerce.oms.inventory.InventoryItem;
import com.ecommerce.oms.inventory.InventoryItemRepository;
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

class CheckoutConcurrencyTest extends IntegrationTestBase {

    @Autowired private CheckoutService checkoutService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private InventoryItemRepository inventoryRepository;

    private Product gpu;

    @BeforeEach
    void setUp() {
        gpu = fixtures.product(fixtures.category("Electronics", "0.18"), "GPU-1", "GPU", "500.00");
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
    void concurrentBuyersNeverOversellAcrossWarehouses() throws Exception {
        fixtures.stock(gpu, fixtures.warehouse("W1"), 3, 0);
        fixtures.stock(gpu, fixtures.warehouse("W2"), 2, 0);
        List<Callable<Boolean>> buyers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            User buyer = fixtures.customer("buyer" + i + "@test.local");
            fixtures.cartWith(buyer, gpu, 1);
            AppUserDetails principal = AppUserDetails.from(buyer);
            buyers.add(() -> {
                try {
                    checkoutService.checkout(principal, checkoutRequest(), null);
                    return true;
                } catch (InsufficientStockException e) {
                    return false;
                }
            });
        }

        int successes = runConcurrently(buyers);

        assertThat(successes).isEqualTo(5);
        assertThat(orderRepository.count()).isEqualTo(5);
        List<InventoryItem> rows = inventoryRepository.findAll();
        assertThat(rows.stream().mapToInt(InventoryItem::getReserved).sum()).isEqualTo(5);
        assertThat(rows).allSatisfy(row -> assertThat(row.getReserved()).isLessThanOrEqualTo(row.getOnHand()));
    }

    @Test
    void sameCustomerDoubleSubmitCreatesOneOrder() throws Exception {
        fixtures.stock(gpu, fixtures.warehouse("W1"), 10, 0);
        User alice = fixtures.customer("alice@test.local");
        fixtures.cartWith(alice, gpu, 1);
        AppUserDetails principal = AppUserDetails.from(alice);
        Callable<Boolean> submit = () -> {
            try {
                checkoutService.checkout(principal, checkoutRequest(), null);
                return true;
            } catch (BadRequestException e) {
                assertThat(e).hasMessage("Cart is empty");
                return false;
            }
        };

        int successes = runConcurrently(List.of(submit, submit));

        assertThat(successes).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(inventoryRepository.findAll().getFirst().getReserved()).isEqualTo(1);
    }
}
