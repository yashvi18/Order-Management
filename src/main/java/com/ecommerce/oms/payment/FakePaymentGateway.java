package com.ecommerce.oms.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Deterministic stand-in for a real PSP; test tokens select the outcome. */
@Component
public class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    @Override
    public GatewayResult charge(String paymentToken, BigDecimal amount, String reference) {
        GatewayResult result = switch (paymentToken) {
            case "tok_declined" -> GatewayResult.declined("Card declined");
            case "tok_insufficient_funds" -> GatewayResult.declined("Insufficient funds");
            default -> GatewayResult.approved("ch_" + UUID.randomUUID().toString().replace("-", ""));
        };
        log.info("Charge {} for {} -> {}", amount, reference, result.success() ? "approved" : result.failureReason());
        return result;
    }

    @Override
    public GatewayResult refund(String transactionId, BigDecimal amount) {
        log.info("Refund {} on {}", amount, transactionId);
        return GatewayResult.approved("re_" + UUID.randomUUID().toString().replace("-", ""));
    }
}
