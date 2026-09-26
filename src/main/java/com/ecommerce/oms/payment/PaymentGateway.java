package com.ecommerce.oms.payment;

import java.math.BigDecimal;

/** Port to an external PSP (Stripe/Razorpay...). The paymentToken is a client-side tokenised card. */
public interface PaymentGateway {

    GatewayResult charge(String paymentToken, BigDecimal amount, String reference);

    GatewayResult refund(String transactionId, BigDecimal amount);
}
