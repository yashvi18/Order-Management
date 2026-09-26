package com.ecommerce.oms.inventory;

import com.ecommerce.oms.common.ConflictException;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(Long productId, long requested, long available) {
        super("Insufficient stock for product %d: requested %d, available %d".formatted(productId, requested, available));
    }
}
