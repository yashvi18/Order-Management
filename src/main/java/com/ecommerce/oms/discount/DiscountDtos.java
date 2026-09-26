package com.ecommerce.oms.discount;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class DiscountDtos {

    private DiscountDtos() {
    }

    public record DiscountRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String code,
            @Size(max = 255) String description,
            @NotNull DiscountType type,
            @NotNull @DecimalMin("0.01") BigDecimal value,
            @DecimalMin("0.00") BigDecimal minOrderAmount,
            @DecimalMin("0.01") BigDecimal maxDiscountAmount,
            Instant validFrom,
            Instant validUntil,
            @Min(1) Integer usageLimit,
            Boolean active) {
    }

    public record DiscountResponse(Long id, String code, String description, DiscountType type, BigDecimal value,
            BigDecimal minOrderAmount, BigDecimal maxDiscountAmount, Instant validFrom, Instant validUntil,
            Integer usageLimit, int timesUsed, boolean active) {
        static DiscountResponse from(Discount d) {
            return new DiscountResponse(d.getId(), d.getCode(), d.getDescription(), d.getType(), d.getValue(),
                    d.getMinOrderAmount(), d.getMaxDiscountAmount(), d.getValidFrom(), d.getValidUntil(),
                    d.getUsageLimit(), d.getTimesUsed(), d.isActive());
        }
    }
}
