package com.ecommerce.oms.pricing;

import java.math.BigDecimal;
import java.util.List;

public record PricingResult(List<PricedLine> lines, BigDecimal subtotal, BigDecimal discountTotal,
        BigDecimal taxTotal, BigDecimal grandTotal) {
}
