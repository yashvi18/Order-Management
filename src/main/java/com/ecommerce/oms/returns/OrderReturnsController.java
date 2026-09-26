package com.ecommerce.oms.returns;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.returns.ReturnDtos.CreateReturnRequest;
import com.ecommerce.oms.returns.ReturnDtos.ReturnResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/returns")
public class OrderReturnsController {

    private final ReturnService returnService;

    public OrderReturnsController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping
    public ResponseEntity<ReturnResponse> request(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long orderId,
            @Valid @RequestBody CreateReturnRequest request) {
        ReturnResponse created = returnService.requestReturn(orderId, me, request);
        return ResponseEntity.created(URI.create("/api/orders/%d/returns".formatted(orderId))).body(created);
    }

    @GetMapping
    public List<ReturnResponse> list(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long orderId) {
        return returnService.listForOrder(orderId, me.id());
    }
}
