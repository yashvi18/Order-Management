package com.ecommerce.oms.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.inventory.InventoryItem;
import com.ecommerce.oms.inventory.InventoryItemRepository;
import com.ecommerce.oms.order.OrderStatus;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FulfillmentConcurrencyTest extends IntegrationTestBase {

    @Autowired private InventoryItemRepository inventoryRepository;

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
    void concurrentShipUpdatesCommitStockOnce() throws Exception {
        User alice = fixtures.customer("alice@test.local");
        Product pen = fixtures.product(fixtures.category("Stationery", "0.05"), "PEN-1", "Pen", "10.00");
        fixtures.stock(pen, fixtures.warehouse("W1"), 10, 0);
        fixtures.cartWith(alice, pen, 2);
        long orderId = checkout(alice);
        outboxProcessor.processBatch();
        AppUserDetails admin = AppUserDetails.from(fixtures.admin());
        fulfillmentService.updateStatus(orderId, OrderStatus.PACKED, admin, null);
        Callable<Boolean> ship = () -> {
            try {
                fulfillmentService.updateStatus(orderId, OrderStatus.SHIPPED, admin, null);
                return true;
            } catch (ConflictException e) {
                return false;
            }
        };

        int successes = runConcurrently(List.of(ship, ship));

        assertThat(successes).isEqualTo(1);
        InventoryItem row = inventoryRepository.findAll().getFirst();
        assertThat(row.getOnHand()).isEqualTo(8);
        assertThat(row.getReserved()).isZero();
    }
}
