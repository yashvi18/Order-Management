package com.ecommerce.oms.returns;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.Role;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.Money;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.events.OrderEventPublisher;
import com.ecommerce.oms.events.OrderEventType;
import com.ecommerce.oms.fulfillment.WarehouseAccessPolicy;
import com.ecommerce.oms.inventory.InventoryService;
import com.ecommerce.oms.inventory.StockAllocation;
import com.ecommerce.oms.order.CustomerOrder;
import com.ecommerce.oms.order.OrderItem;
import com.ecommerce.oms.order.OrderRepository;
import com.ecommerce.oms.order.OrderStatus;
import com.ecommerce.oms.payment.PaymentService;
import com.ecommerce.oms.returns.ReturnDtos.CreateReturnRequest;
import com.ecommerce.oms.returns.ReturnDtos.ReturnLine;
import com.ecommerce.oms.returns.ReturnDtos.ReturnResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnService {

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderEventPublisher eventPublisher;
    private final WarehouseAccessPolicy accessPolicy;
    private final Clock clock;
    private final int windowDays;

    public ReturnService(OrderRepository orderRepository, ReturnRequestRepository returnRepository,
            InventoryService inventoryService, PaymentService paymentService, OrderEventPublisher eventPublisher,
            WarehouseAccessPolicy accessPolicy, Clock clock, @Value("${oms.returns.window-days:30}") int windowDays) {
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
        this.windowDays = windowDays;
    }

    @Transactional
    public ReturnResponse requestReturn(Long orderId, AppUserDetails customer, CreateReturnRequest request) {
        CustomerOrder order = orderRepository.lockById(orderId)
                .filter(o -> o.getCustomer().getId().equals(customer.id()))
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.PARTIALLY_RETURNED) {
            throw new ConflictException("Only delivered orders can be returned");
        }
        if (clock.instant().isAfter(order.getDeliveredAt().plus(Duration.ofDays(windowDays)))) {
            throw new BadRequestException("The " + windowDays + "-day return window has expired");
        }
        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setCustomerOrder(order);
        returnRequest.setReason(request.reason().trim());
        Set<Long> seen = new HashSet<>();
        for (ReturnLine line : request.items()) {
            if (!seen.add(line.orderItemId())) {
                throw new BadRequestException("Order item " + line.orderItemId() + " is listed twice");
            }
            OrderItem item = order.getItems().stream().filter(i -> i.getId().equals(line.orderItemId())).findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Order item " + line.orderItemId() + " does not belong to this order"));
            long pending = returnRepository.sumQuantityByOrderItemAndStatus(item.getId(), ReturnStatus.REQUESTED);
            long returnable = item.getQuantity() - item.getReturnedQuantity() - pending;
            if (line.quantity() > returnable) {
                throw new BadRequestException("Cannot return %d x %s; only %d returnable"
                        .formatted(line.quantity(), item.getSku(), returnable));
            }
            ReturnItem returnItem = new ReturnItem();
            returnItem.setOrderItem(item);
            returnItem.setQuantity(line.quantity());
            returnRequest.addItem(returnItem);
        }
        returnRepository.save(returnRequest);
        eventPublisher.publish(OrderEventType.RETURN_REQUESTED, order.getId(), customer.id(), customer.email(),
                "Return #" + returnRequest.getId() + " requested for order " + order.getOrderNumber());
        return ReturnResponse.from(returnRequest);
    }

    /** Goods arrived back: restock, refund the paid share of each unit, move the order to (PARTIALLY_)RETURNED. */
    @Transactional
    public ReturnResponse receive(Long returnId, AppUserDetails actor, String note) {
        ReturnRequest returnRequest = requirePending(returnId, actor);
        CustomerOrder order = orderRepository.lockById(returnRequest.getCustomerOrder().getId()).orElseThrow();
        BigDecimal refundTotal = Money.ZERO;
        List<StockAllocation> restock = new ArrayList<>();
        for (ReturnItem returnItem : returnRequest.getItems()) {
            OrderItem item = returnItem.getOrderItem();
            int quantity = returnItem.getQuantity();
            BigDecimal remaining = item.getLineTotal().subtract(item.getRefundedAmount());
            BigDecimal refund = item.getReturnedQuantity() + quantity == item.getQuantity()
                    ? remaining
                    : item.getLineTotal().multiply(BigDecimal.valueOf(quantity))
                            .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP).min(remaining);
            item.setReturnedQuantity(item.getReturnedQuantity() + quantity);
            item.setRefundedAmount(item.getRefundedAmount().add(refund));
            refundTotal = refundTotal.add(refund);
            restock.add(new StockAllocation(item.getProduct().getId(),
                    item.getAllocations().getFirst().getWarehouse().getId(), quantity));
        }
        inventoryService.restock(restock);
        if (refundTotal.signum() > 0) {
            paymentService.refund(order.getId(), refundTotal, "Return #" + returnRequest.getId());
        }
        resolve(returnRequest, ReturnStatus.RECEIVED, actor, note);
        returnRequest.setRefundAmount(refundTotal);
        boolean fullyReturned = order.getItems().stream().allMatch(i -> i.getReturnedQuantity() == i.getQuantity());
        order.transitionTo(fullyReturned ? OrderStatus.RETURNED : OrderStatus.PARTIALLY_RETURNED, actor.email(),
                "Return #" + returnRequest.getId() + " received");
        eventPublisher.publish(OrderEventType.REFUND_ISSUED, order.getId(), order.getCustomer().getId(), actor.email(),
                "Refund of " + refundTotal + " issued for return #" + returnRequest.getId());
        return ReturnResponse.from(returnRequest);
    }

    @Transactional
    public ReturnResponse reject(Long returnId, AppUserDetails actor, String note) {
        ReturnRequest returnRequest = requirePending(returnId, actor);
        resolve(returnRequest, ReturnStatus.REJECTED, actor, note);
        CustomerOrder order = returnRequest.getCustomerOrder();
        eventPublisher.publish(OrderEventType.RETURN_REJECTED, order.getId(), order.getCustomer().getId(), actor.email(),
                "Return #" + returnRequest.getId() + " rejected" + (note == null ? "" : ": " + note));
        return ReturnResponse.from(returnRequest);
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> listForOrder(Long orderId, Long customerId) {
        orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return returnRepository.findByCustomerOrderIdOrderByIdAsc(orderId).stream().map(ReturnResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> listForWarehouse(AppUserDetails actor, ReturnStatus status) {
        List<ReturnRequest> requests = actor.role() == Role.ADMIN
                ? returnRepository.findByStatusOrderByIdAsc(status)
                : returnRepository.findForWarehouse(actor.warehouseId(), status);
        return requests.stream().map(ReturnResponse::from).toList();
    }

    private ReturnRequest requirePending(Long returnId, AppUserDetails actor) {
        // Lock the return row before reading its status: otherwise two concurrent receives can both pass the
        // REQUESTED check, and the second (waiting only on the order lock taken later in receive()) would call
        // the payment gateway a second time before @Version catches the conflict at commit. Locking here makes
        // the second caller block until the first commits, so it observes RECEIVED/REJECTED and gets 409 before
        // touching inventory or the gateway. Lock order is return row, then order row (requestReturn only takes
        // the order row lock), so this cannot deadlock against it.
        ReturnRequest returnRequest = returnRepository.lockById(returnId)
                .orElseThrow(() -> new NotFoundException("Return not found"));
        accessPolicy.check(returnRequest.getCustomerOrder().getId(), actor);
        if (returnRequest.getStatus() != ReturnStatus.REQUESTED) {
            throw new ConflictException("Return #" + returnId + " is already " + returnRequest.getStatus());
        }
        return returnRequest;
    }

    private void resolve(ReturnRequest returnRequest, ReturnStatus status, AppUserDetails actor, String note) {
        returnRequest.setStatus(status);
        returnRequest.setResolvedAt(clock.instant());
        returnRequest.setResolvedBy(actor.email());
        returnRequest.setResolutionNote(note);
    }
}
