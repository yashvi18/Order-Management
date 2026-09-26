package com.ecommerce.oms.inventory;

/** "quantity units of productId come from warehouseId" — the unit of reserve/release/ship/restock. */
public record StockAllocation(Long productId, Long warehouseId, int quantity) {
}
