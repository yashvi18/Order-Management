package com.ecommerce.oms.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the event in the SAME transaction as the business change, so an event exists if and only if the
 * change committed. Nothing is executed here — the OutboxProcessor does the slow work later.
 */
@Component
public class OrderEventPublisher {

    private final OutboxRepository outboxRepository;

    public OrderEventPublisher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(OrderEventType type, Long orderId, Long customerId, String actor, String details) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(type);
        event.setOrderId(orderId);
        event.setCustomerId(customerId);
        event.setActor(actor);
        event.setDetails(details);
        outboxRepository.save(event);
    }
}
