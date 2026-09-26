package com.ecommerce.oms.order;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.common.PageResponse;
import com.ecommerce.oms.order.OrderDtos.CancelOrderRequest;
import com.ecommerce.oms.order.OrderDtos.OrderResponse;
import com.ecommerce.oms.order.OrderDtos.OrderSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(@RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listAll(status, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getAny(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(id, me, request == null ? null : request.reason());
    }
}
