package com.ecommerce.oms.fulfillment;

import com.ecommerce.oms.events.OrderEventHandler;
import com.ecommerce.oms.events.OrderEventPublisher;
import com.ecommerce.oms.events.OrderEventType;
import com.ecommerce.oms.events.OutboxEvent;
import com.ecommerce.oms.order.CustomerOrder;
import com.ecommerce.oms.order.OrderRepository;
import com.ecommerce.oms.order.OrderStatus;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Downstream step 1: hands a freshly placed order to the warehouses its stock was reserved in and confirms it.
 * Idempotent: an order that is no longer PLACED (e.g. cancelled before routing ran) is left untouched.
 */
@Component
public class FulfillmentRoutingHandler implements OrderEventHandler {

    static final String ACTOR = "system:fulfillment-router";
    private static final Logger log = LoggerFactory.getLogger(FulfillmentRoutingHandler.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final Clock clock;

    public FulfillmentRoutingHandler(OrderRepository orderRepository, OrderEventPublisher eventPublisher, Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public boolean supports(OrderEventType type) {
        return type == OrderEventType.ORDER_PLACED;
    }

    @Override
    public void handle(OutboxEvent event) {
        CustomerOrder order = orderRepository.lockById(event.getOrderId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PLACED) {
            log.info("Skipping routing for order {} (status {})", event.getOrderId(),
                    order == null ? "missing" : order.getStatus());
            return;
        }
        String warehouses = order.warehouseCodes();
        order.transitionTo(OrderStatus.CONFIRMED, ACTOR, "Routed to warehouses: " + warehouses, clock.instant());
        eventPublisher.publish(OrderEventType.ORDER_STATUS_CHANGED, order.getId(), event.getCustomerId(), ACTOR,
                "Order " + order.getOrderNumber() + " confirmed and routed to " + warehouses);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
