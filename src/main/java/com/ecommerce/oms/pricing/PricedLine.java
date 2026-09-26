package com.ecommerce.oms.pricing;

import java.math.BigDecimal;

public record PricedLine(Long productId, BigDecimal unitPrice, int quantity, BigDecimal taxRate,
        BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
}
