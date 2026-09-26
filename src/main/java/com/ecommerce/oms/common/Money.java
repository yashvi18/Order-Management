package com.ecommerce.oms.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
