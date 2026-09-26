package com.ecommerce.oms.order;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.Role;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.common.PageResponse;
import com.ecommerce.oms.common.Pageables;
import com.ecommerce.oms.events.OrderEventPublisher;
import com.ecommerce.oms.events.OrderEventType;
import com.ecommerce.oms.inventory.InventoryService;
import com.ecommerce.oms.order.OrderDtos.OrderResponse;
import com.ecommerce.oms.order.OrderDtos.OrderSummaryResponse;
import com.ecommerce.oms.payment.PaymentService;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    public static final Set<String> SORTABLE = Set.of("id", "placedAt", "grandTotal", "status");

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper, InventoryService inventoryService,
            PaymentService paymentService, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listForCustomer(Long customerId, Pageable pageable) {
        Pageables.requireSortableBy(pageable, SORTABLE);
        return PageResponse.from(orderRepository.findByCustomerId(customerId, pageable).map(orderMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public OrderResponse getForCustomer(Long orderId, Long customerId) {
        return orderMapper.toResponse(orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(OrderService::notFound));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listAll(OrderStatus status, Pageable pageable) {
        Pageables.requireSortableBy(pageable, SORTABLE);
        return PageResponse.from((status == null ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable)).map(orderMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public OrderResponse getAny(Long orderId) {
        return orderMapper.toResponse(orderRepository.findById(orderId).orElseThrow(OrderService::notFound));
    }

    /** Allowed only while PLACED/CONFIRMED (state machine): releases reserved stock and refunds in full. */
    @Transactional
    public OrderResponse cancel(Long orderId, AppUserDetails actor, String reason) {
        // Lock the row before the status check: the refund below is an external call that a rollback cannot undo,
        // so the router or a warehouse update must not be able to move the order on while we cancel it.
        CustomerOrder order = orderRepository.lockById(orderId)
                .filter(o -> actor.role() == Role.ADMIN || o.getCustomer().getId().equals(actor.id()))
                .orElseThrow(OrderService::notFound);
        order.transitionTo(OrderStatus.CANCELLED, actor.email(),
                reason == null || reason.isBlank() ? "Cancelled" : reason);
        inventoryService.releaseReservations(order.stockAllocations());
        if (order.getGrandTotal().signum() > 0) {
            paymentService.refund(order.getId(), order.getGrandTotal(), "Order cancelled");
        }
        eventPublisher.publish(OrderEventType.ORDER_CANCELLED, order.getId(), order.getCustomer().getId(),
                actor.email(), "Order " + order.getOrderNumber() + " cancelled; refund of " + order.getGrandTotal() + " issued");
        return orderMapper.toResponse(order);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Order not found");
    }
}
