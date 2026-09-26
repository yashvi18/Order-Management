package com.ecommerce.oms.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InventoryReservationTest extends IntegrationTestBase {

    @Autowired private InventoryService inventoryService;

    private Product product;
    private Warehouse w1;
    private Warehouse w2;

    @BeforeEach
    void setUp() {
        product = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-1", "Laptop", "1000.00");
        w1 = fixtures.warehouse("W1");
        w2 = fixtures.warehouse("W2");
    }

    private List<StockAllocation> reserve(int quantity) {
        return tx.execute(s -> inventoryService.reserve(Map.of(product.getId(), quantity)));
    }

    @Test
    void usesASingleWarehouseWhenOneCanShipEverything() {
        fixtures.stock(product, w1, 3, 0);
        fixtures.stock(product, w2, 10, 0);
        assertThat(reserve(5)).containsExactly(new StockAllocation(product.getId(), w2.getId(), 5));
        assertThat(fixtures.inventory(product, w2).getReserved()).isEqualTo(5);
        assertThat(fixtures.inventory(product, w1).getReserved()).isZero();
    }

    @Test
    void splitsAcrossWarehousesWhenNeeded() {
        fixtures.stock(product, w1, 3, 0);
        fixtures.stock(product, w2, 2, 0);
        assertThat(reserve(4)).containsExactly(
                new StockAllocation(product.getId(), w1.getId(), 3),
                new StockAllocation(product.getId(), w2.getId(), 1));
    }

    @Test
    void insufficientStockThrowsAndReservesNothing() {
        fixtures.stock(product, w1, 2, 0);
        fixtures.stock(product, w2, 1, 1);
        assertThatThrownBy(() -> reserve(3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 3, available 2");
        assertThat(fixtures.inventory(product, w1).getReserved()).isZero();
    }

    @Test
    void inactiveWarehousesAreSkipped() {
        fixtures.stock(product, fixtures.deactivate(w1), 10, 0);
        fixtures.stock(product, w2, 1, 0);
        assertThatThrownBy(() -> reserve(2)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void commitReleaseAndRestockAdjustCounters() {
        fixtures.stock(product, w1, 10, 0);
        reserve(4);
        tx.executeWithoutResult(s -> inventoryService.commitShipment(
                List.of(new StockAllocation(product.getId(), w1.getId(), 3))));
        tx.executeWithoutResult(s -> inventoryService.releaseReservations(
                List.of(new StockAllocation(product.getId(), w1.getId(), 1))));
        tx.executeWithoutResult(s -> inventoryService.restock(
                List.of(new StockAllocation(product.getId(), w1.getId(), 2))));
        InventoryItem item = fixtures.inventory(product, w1);
        assertThat(item.getOnHand()).isEqualTo(9);
        assertThat(item.getReserved()).isZero();
    }

    @Test
    void releasingMoreThanReservedFails() {
        fixtures.stock(product, w1, 10, 1);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> inventoryService.releaseReservations(
                List.of(new StockAllocation(product.getId(), w1.getId(), 2)))))
                .isInstanceOf(IllegalStateException.class);
    }
}
