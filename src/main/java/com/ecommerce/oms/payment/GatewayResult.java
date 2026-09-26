package com.ecommerce.oms.payment;

public record GatewayResult(boolean success, String transactionId, String failureReason) {

    public static GatewayResult approved(String transactionId) {
        return new GatewayResult(true, transactionId, null);
    }

    public static GatewayResult declined(String reason) {
        return new GatewayResult(false, null, reason);
    }
}
