package com.ecommerce.oms.order;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.order.CheckoutService.CheckoutResult;
import com.ecommerce.oms.order.OrderDtos.CheckoutRequest;
import com.ecommerce.oms.order.OrderDtos.OrderResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal AppUserDetails me,
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            throw new BadRequestException("Idempotency-Key must be 1-100 characters");
        }
        CheckoutResult result = checkoutService.checkout(me, request, idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok(result.order());
        }
        return ResponseEntity.created(URI.create("/api/orders/" + result.order().id())).body(result.order());
    }
}
