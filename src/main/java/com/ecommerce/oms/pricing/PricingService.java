package com.ecommerce.oms.pricing;

import com.ecommerce.oms.common.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure pricing math. The order-level discount is spread over lines by subtotal share (remainder on the
 * largest line) so each line knows its net price — needed for per-category tax and for fair partial refunds.
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
        BigDecimal discount = Money.of(discountAmount == null ? BigDecimal.ZERO : discountAmount).min(subtotal);

        int largest = 0;
        for (int i = 1; i < subtotals.size(); i++) {
            if (subtotals.get(i).compareTo(subtotals.get(largest)) > 0) {
                largest = i;
            }
        }
        BigDecimal[] discounts = new BigDecimal[lines.size()];
        BigDecimal allocated = Money.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            if (i == largest) {
                continue;
            }
            discounts[i] = subtotal.signum() == 0 ? Money.ZERO
                    : subtotals.get(i).multiply(discount).divide(subtotal, 2, RoundingMode.HALF_UP);
            allocated = allocated.add(discounts[i]);
        }
        discounts[largest] = discount.subtract(allocated).max(Money.ZERO).min(subtotals.get(largest));

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
}
