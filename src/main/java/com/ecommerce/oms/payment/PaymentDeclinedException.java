package com.ecommerce.oms.payment;

import com.ecommerce.oms.common.ApiException;
import org.springframework.http.HttpStatus;

public class PaymentDeclinedException extends ApiException {

    public PaymentDeclinedException(String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, "Payment declined: " + reason);
    }
}
