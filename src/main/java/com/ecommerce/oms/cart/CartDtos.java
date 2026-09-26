package com.ecommerce.oms.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddItemRequest(@NotNull Long productId, @NotNull @Min(1) @Max(100) Integer quantity) {
    }

    public record UpdateItemRequest(@NotNull @Min(0) @Max(100) Integer quantity) {
    }

    public record CartLine(Long productId, String sku, String name, BigDecimal unitPrice, int quantity,
            BigDecimal lineTotal, boolean purchasable) {
    }

    public record CartResponse(List<CartLine> items, int totalQuantity, BigDecimal subtotal) {
    }
}
