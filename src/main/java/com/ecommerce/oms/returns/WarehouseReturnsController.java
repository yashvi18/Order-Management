package com.ecommerce.oms.returns;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.returns.ReturnDtos.ResolveReturnRequest;
import com.ecommerce.oms.returns.ReturnDtos.ReturnResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouse/returns")
public class WarehouseReturnsController {

    private final ReturnService returnService;

    public WarehouseReturnsController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    public List<ReturnResponse> list(@AuthenticationPrincipal AppUserDetails me,
            @RequestParam(defaultValue = "REQUESTED") ReturnStatus status) {
        return returnService.listForWarehouse(me, status);
    }

    @PostMapping("/{id}/receive")
    public ReturnResponse receive(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
            @Valid @RequestBody(required = false) ResolveReturnRequest request) {
        return returnService.receive(id, me, request == null ? null : request.note());
    }

    @PostMapping("/{id}/reject")
    public ReturnResponse reject(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
            @Valid @RequestBody(required = false) ResolveReturnRequest request) {
        return returnService.reject(id, me, request == null ? null : request.note());
    }
}
