package com.ecommerce.oms.pricing;

import java.math.BigDecimal;

public record PricingLine(Long productId, BigDecimal unitPrice, int quantity, BigDecimal taxRate) {
}
