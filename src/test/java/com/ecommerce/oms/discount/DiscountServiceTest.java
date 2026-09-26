package com.ecommerce.oms.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.oms.common.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiscountServiceTest {

    private final DiscountRepository repository = mock(DiscountRepository.class);
    private final DiscountService service = new DiscountService(repository);
    private final Instant now = Instant.parse("2026-09-26T10:00:00Z");

    private Discount discount(DiscountType type, String value) {
        Discount discount = new Discount();
        discount.setId(1L);
        discount.setCode("SAVE");
        discount.setType(type);
        discount.setValue(new BigDecimal(value));
        when(repository.findByCodeIgnoreCase("SAVE")).thenReturn(Optional.of(discount));
        return discount;
    }

    @Test
    void percentageOfSubtotal() {
        discount(DiscountType.PERCENTAGE, "10");
        assertThat(service.apply("save", new BigDecimal("250.00"), now).amount()).isEqualByComparingTo("25.00");
    }

    @Test
    void percentageIsCappedByMaxDiscount() {
        discount(DiscountType.PERCENTAGE, "50").setMaxDiscountAmount(new BigDecimal("200.00"));
        assertThat(service.apply("SAVE", new BigDecimal("1000.00"), now).amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void fixedAmountNeverExceedsSubtotal() {
        discount(DiscountType.FIXED_AMOUNT, "300.00");
        assertThat(service.apply("SAVE", new BigDecimal("250.00"), now).amount()).isEqualByComparingTo("250.00");
    }

    @Test
    void unknownCodeIsRejected() {
        when(repository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.apply("NOPE", BigDecimal.TEN, now))
                .isInstanceOf(BadRequestException.class).hasMessage("Invalid discount code");
    }

    @Test
    void inactiveExpiredNotYetValidBelowMinimumAndExhaustedAreRejected() {
        Discount d = discount(DiscountType.PERCENTAGE, "10");
        BigDecimal subtotal = new BigDecimal("100.00");

        d.setActive(false);
        assertThatThrownBy(() -> service.apply("SAVE", subtotal, now)).hasMessageContaining("not active");
        d.setActive(true);

        d.setValidUntil(now);
        assertThatThrownBy(() -> service.apply("SAVE", subtotal, now)).hasMessageContaining("expired");
        d.setValidUntil(null);

        d.setValidFrom(now.plusSeconds(60));
        assertThatThrownBy(() -> service.apply("SAVE", subtotal, now)).hasMessageContaining("not yet valid");
        d.setValidFrom(null);

        d.setMinOrderAmount(new BigDecimal("100.01"));
        assertThatThrownBy(() -> service.apply("SAVE", subtotal, now)).hasMessageContaining("at least 100.01");
        d.setMinOrderAmount(null);

        d.setUsageLimit(2);
        d.setTimesUsed(2);
        assertThatThrownBy(() -> service.apply("SAVE", subtotal, now)).hasMessageContaining("usage limit");
    }
}
