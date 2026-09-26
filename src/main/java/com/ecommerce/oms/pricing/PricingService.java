package com.ecommerce.oms.pricing;

import com.ecommerce.oms.common.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure pricing math. The order-level discount is spread over lines by subtotal share using largest-remainder
 * allocation so each line knows its net price — needed for per-category tax and for fair partial refunds.
 */
@Component
public class PricingService {

    public PricingResult price(List<PricingLine> lines, BigDecimal discountAmount) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Nothing to price");
        }
        List<BigDecimal> subtotals = lines.stream()
                .map(l -> Money.of(l.unitPrice().multiply(BigDecimal.valueOf(l.quantity()))))
                .toList();
        BigDecimal subtotal = subtotals.stream().reduce(Money.ZERO, BigDecimal::add);
        // Floor negative discounts to zero
        BigDecimal rawDiscount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        if (rawDiscount.signum() < 0) {
            rawDiscount = BigDecimal.ZERO;
        }
        BigDecimal discount = Money.of(rawDiscount).min(subtotal);

        // Allocate discount using largest-remainder method
        BigDecimal[] discounts = allocateDiscount(subtotals, subtotal, discount);

        List<PricedLine> priced = new ArrayList<>();
        BigDecimal discountTotal = Money.ZERO;
        BigDecimal taxTotal = Money.ZERO;
        BigDecimal grandTotal = Money.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            PricingLine line = lines.get(i);
            BigDecimal taxable = subtotals.get(i).subtract(discounts[i]);
            BigDecimal tax = Money.of(taxable.multiply(line.taxRate()));
            BigDecimal total = taxable.add(tax);
            priced.add(new PricedLine(line.productId(), Money.of(line.unitPrice()), line.quantity(), line.taxRate(),
                    subtotals.get(i), discounts[i], tax, total));
            discountTotal = discountTotal.add(discounts[i]);
            taxTotal = taxTotal.add(tax);
            grandTotal = grandTotal.add(total);
        }
        return new PricingResult(priced, subtotal, discountTotal, taxTotal, grandTotal);
    }

    /**
     * Allocate discount using largest-remainder method to avoid overshooting.
     * Computes exact share at high precision, floors to 2 decimals, then distributes
     * leftover cents to lines with largest fractional remainders (ties: larger subtotal, then lower index).
     */
    private BigDecimal[] allocateDiscount(List<BigDecimal> subtotals, BigDecimal subtotal, BigDecimal discount) {
        int n = subtotals.size();
        BigDecimal[] discounts = new BigDecimal[n];

        // Handle zero subtotal case
        if (subtotal.signum() == 0) {
            for (int i = 0; i < n; i++) {
                discounts[i] = Money.ZERO;
            }
            return discounts;
        }

        // Compute exact shares at high precision (DECIMAL128)
        BigDecimal[] exactShares = new BigDecimal[n];
        BigDecimal[] baseShares = new BigDecimal[n];
        BigDecimal baseShareSum = Money.ZERO;

        for (int i = 0; i < n; i++) {
            // Compute exact share with high precision
            exactShares[i] = subtotals.get(i).multiply(discount).divide(subtotal, MathContext.DECIMAL128);
            // Floor to 2 decimals
            baseShares[i] = exactShares[i].setScale(2, RoundingMode.DOWN);
            baseShareSum = baseShareSum.add(baseShares[i]);
        }

        // Calculate leftover cents (each 0.01 unit)
        BigDecimal remainder = discount.subtract(baseShareSum);
        int leftoverCents = remainder.movePointRight(2).intValueExact(); // Convert to cents

        // Build list of (fractional_remainder, subtotal, index) for sorting
        List<RemainderLine> remainderLines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal fractionalPart = exactShares[i].subtract(baseShares[i]);
            remainderLines.add(new RemainderLine(fractionalPart, subtotals.get(i), i));
        }

        // Sort by: fractional remainder DESC, subtotal DESC, index ASC
        remainderLines.sort((a, b) -> {
            int cmp = b.fractionalRemainder.compareTo(a.fractionalRemainder);
            if (cmp != 0) return cmp;
            cmp = b.subtotal.compareTo(a.subtotal);
            if (cmp != 0) return cmp;
            return Integer.compare(a.index, b.index);
        });

        // Assign base shares
        for (int i = 0; i < n; i++) {
            discounts[i] = baseShares[i];
        }

        // Distribute leftover cents to the top leftoverCents lines
        for (int i = 0; i < leftoverCents; i++) {
            int lineIdx = remainderLines.get(i).index;
            discounts[lineIdx] = discounts[lineIdx].add(BigDecimal.valueOf(0.01));
        }

        return discounts;
    }

    /**
     * Helper class to track fractional remainders for sorting.
     */
    private static class RemainderLine {
        BigDecimal fractionalRemainder;
        BigDecimal subtotal;
        int index;

        RemainderLine(BigDecimal fractionalRemainder, BigDecimal subtotal, int index) {
            this.fractionalRemainder = fractionalRemainder;
            this.subtotal = subtotal;
            this.index = index;
        }
    }
}
