package com.ecommerce.oms.discount;

import java.math.BigDecimal;

public record AppliedDiscount(Long discountId, String code, BigDecimal amount) {
}
