package com.ecommerce.oms.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    private static PricingLine line(long productId, String price, int qty, String taxRate) {
        return new PricingLine(productId, new BigDecimal(price), qty, new BigDecimal(taxRate));
    }

    @Test
    void appliesPerCategoryTaxWithoutDiscount() {
        PricingResult result = pricing.price(List.of(line(1, "100.00", 2, "0.18"), line(2, "50.00", 1, "0.05")),
                BigDecimal.ZERO);
        assertThat(result.subtotal()).isEqualByComparingTo("250.00");
        assertThat(result.discountTotal()).isEqualByComparingTo("0.00");
        assertThat(result.taxTotal()).isEqualByComparingTo("38.50");
        assertThat(result.grandTotal()).isEqualByComparingTo("288.50");
    }

    @Test
    void allocatesDiscountProportionallyAndTaxesTheDiscountedAmount() {
        PricingResult result = pricing.price(List.of(line(1, "100.00", 2, "0.18"), line(2, "50.00", 1, "0.05")),
                new BigDecimal("25.00"));
        PricedLine a = result.lines().get(0);
        PricedLine b = result.lines().get(1);
        assertThat(a.discount()).isEqualByComparingTo("20.00");
        assertThat(a.tax()).isEqualByComparingTo("32.40");
        assertThat(a.total()).isEqualByComparingTo("212.40");
        assertThat(b.discount()).isEqualByComparingTo("5.00");
        assertThat(b.tax()).isEqualByComparingTo("2.25");
        assertThat(b.total()).isEqualByComparingTo("47.25");
        assertThat(result.discountTotal()).isEqualByComparingTo("25.00");
        assertThat(result.taxTotal()).isEqualByComparingTo("34.65");
        assertThat(result.grandTotal()).isEqualByComparingTo("259.65");
    }

    @Test
    void roundingRemainderGoesToTheLargestLineSoDiscountsSumExactly() {
        PricingResult result = pricing.price(List.of(
                line(1, "10.00", 1, "0"), line(2, "10.00", 1, "0"), line(3, "10.00", 1, "0")),
                new BigDecimal("10.00"));
        assertThat(result.lines()).extracting(PricedLine::discount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.34"), new BigDecimal("3.33"), new BigDecimal("3.33"));
        assertThat(result.discountTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void discountLargerThanSubtotalIsClamped() {
        PricingResult result = pricing.price(List.of(line(1, "20.00", 1, "0.10")), new BigDecimal("50.00"));
        assertThat(result.discountTotal()).isEqualByComparingTo("20.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void grandTotalEqualsSumOfLineTotals() {
        PricingResult result = pricing.price(List.of(line(1, "3.33", 3, "0.05"), line(2, "19.99", 2, "0.18")),
                new BigDecimal("7.77"));
        BigDecimal sum = result.lines().stream().map(PricedLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(result.grandTotal()).isEqualByComparingTo(sum);
    }

    @Test
    void equalLinesWithTinyDiscountNeverOverAllocate() {
        PricingResult result = pricing.price(List.of(
                line(1, "10.00", 1, "0"), line(2, "10.00", 1, "0"), line(3, "10.00", 1, "0"), line(4, "10.00", 1, "0")),
                new BigDecimal("0.02"));
        assertThat(result.discountTotal()).isEqualByComparingTo("0.02");
        BigDecimal lineDiscountSum = result.lines().stream().map(PricedLine::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(lineDiscountSum).isEqualByComparingTo("0.02");
        for (PricedLine line : result.lines()) {
            assertThat(line.discount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(line.discount()).isLessThanOrEqualTo(line.subtotal());
        }
    }

    @Test
    void negativeDiscountIsTreatedAsZero() {
        PricingResult result = pricing.price(List.of(line(1, "20.00", 1, "0")), new BigDecimal("-5.00"));
        assertThat(result.discountTotal()).isEqualByComparingTo("0.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("20.00");
    }
}
