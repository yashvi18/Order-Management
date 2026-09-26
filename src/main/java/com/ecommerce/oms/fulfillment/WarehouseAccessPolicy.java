package com.ecommerce.oms.fulfillment;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.Role;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.order.OrderAllocationRepository;
import org.springframework.stereotype.Component;

/** Staff act only on orders that ship (at least partly) from their own warehouse; others get 404 (no leak). */
@Component
public class WarehouseAccessPolicy {

    private final OrderAllocationRepository allocationRepository;

    public WarehouseAccessPolicy(OrderAllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    public void check(Long orderId, AppUserDetails actor) {
        if (actor.role() == Role.ADMIN) {
            return;
        }
        boolean allowed = actor.role() == Role.WAREHOUSE_STAFF && actor.warehouseId() != null
                && allocationRepository.existsByOrderItem_CustomerOrder_IdAndWarehouse_Id(orderId, actor.warehouseId());
        if (!allowed) {
            throw new NotFoundException("Order not found");
        }
    }
}
