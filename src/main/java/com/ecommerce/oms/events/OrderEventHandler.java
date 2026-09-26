package com.ecommerce.oms.events;

import org.springframework.core.Ordered;

/** A downstream consumer of order events. Handlers of one event run in one transaction, in getOrder() order. */
public interface OrderEventHandler extends Ordered {

    boolean supports(OrderEventType type);

    void handle(OutboxEvent event);
}
