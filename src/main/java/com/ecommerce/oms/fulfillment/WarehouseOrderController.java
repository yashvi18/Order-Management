package com.ecommerce.oms.fulfillment;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.common.PageResponse;
import com.ecommerce.oms.order.OrderDtos.OrderResponse;
import com.ecommerce.oms.order.OrderDtos.OrderSummaryResponse;
import com.ecommerce.oms.order.OrderDtos.StatusUpdateRequest;
import com.ecommerce.oms.order.OrderStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouse/orders")
public class WarehouseOrderController {

    private final FulfillmentService fulfillmentService;

    public WarehouseOrderController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> queue(@AuthenticationPrincipal AppUserDetails me,
            @RequestParam(defaultValue = "CONFIRMED") OrderStatus status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return fulfillmentService.queue(me, status, pageable);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return fulfillmentService.updateStatus(id, request.status(), me, request.note());
    }
}
