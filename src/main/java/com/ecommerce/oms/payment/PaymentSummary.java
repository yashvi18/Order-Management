package com.ecommerce.oms.payment;

import java.math.BigDecimal;

public record PaymentSummary(PaymentStatus status, BigDecimal amount, BigDecimal refundedAmount, String transactionId) {

    static PaymentSummary from(Payment payment) {
        return new PaymentSummary(payment.getStatus(), payment.getAmount(), payment.getRefundedAmount(),
                payment.getGatewayTransactionId());
    }
}
