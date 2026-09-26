package com.ecommerce.oms.fulfillment;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.Role;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.ForbiddenException;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.common.PageResponse;
import com.ecommerce.oms.events.OrderEventPublisher;
import com.ecommerce.oms.events.OrderEventType;
import com.ecommerce.oms.inventory.InventoryService;
import com.ecommerce.oms.order.CustomerOrder;
import com.ecommerce.oms.order.OrderDtos.OrderResponse;
import com.ecommerce.oms.order.OrderDtos.OrderSummaryResponse;
import com.ecommerce.oms.order.OrderMapper;
import com.ecommerce.oms.order.OrderRepository;
import com.ecommerce.oms.order.OrderStatus;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {

    private static final Set<OrderStatus> STAFF_TARGETS =
            EnumSet.of(OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryService inventoryService;
    private final OrderEventPublisher eventPublisher;
    private final WarehouseAccessPolicy accessPolicy;

    public FulfillmentService(OrderRepository orderRepository, OrderMapper orderMapper,
            InventoryService inventoryService, OrderEventPublisher eventPublisher, WarehouseAccessPolicy accessPolicy) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> queue(AppUserDetails actor, OrderStatus status, Pageable pageable) {
        if (actor.role() == Role.ADMIN) {
            return PageResponse.from(orderRepository.findByStatus(status, pageable).map(orderMapper::toSummary));
        }
        if (actor.warehouseId() == null) {
            throw new ForbiddenException("Staff account has no warehouse");
        }
        return PageResponse.from(orderRepository.findForWarehouse(actor.warehouseId(), status, pageable)
                .map(orderMapper::toSummary));
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus target, AppUserDetails actor, String note) {
        if (!STAFF_TARGETS.contains(target)) {
            throw new BadRequestException("Warehouse updates may only set PACKED, SHIPPED or DELIVERED");
        }
        accessPolicy.check(orderId, actor);
        CustomerOrder order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        order.transitionTo(target, actor.email(), note);
        if (target == OrderStatus.SHIPPED) {
            inventoryService.commitShipment(order.stockAllocations());
        }
        eventPublisher.publish(OrderEventType.ORDER_STATUS_CHANGED, order.getId(), order.getCustomer().getId(),
                actor.email(), "Order " + order.getOrderNumber() + " is now " + target);
        return orderMapper.toResponse(order);
    }
}
