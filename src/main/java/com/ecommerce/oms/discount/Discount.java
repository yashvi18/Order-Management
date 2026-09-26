package com.ecommerce.oms.discount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "discounts")
@Getter
@Setter
@NoArgsConstructor
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    /** Percent (0-100] for PERCENTAGE, currency amount for FIXED_AMOUNT. */
    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    private Instant validFrom;

    private Instant validUntil;

    private Integer usageLimit;

    @Column(nullable = false)
    private int timesUsed;

    @Column(nullable = false)
    private boolean active = true;
}
