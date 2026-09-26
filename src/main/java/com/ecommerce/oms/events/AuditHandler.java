package com.ecommerce.oms.events;

import org.springframework.stereotype.Component;

@Component
public class AuditHandler implements OrderEventHandler {

    private final AuditLogRepository auditLogRepository;

    public AuditHandler(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public boolean supports(OrderEventType type) {
        return true;
    }

    @Override
    public void handle(OutboxEvent event) {
        AuditLog log = new AuditLog();
        log.setEventType(event.getEventType());
        log.setOrderId(event.getOrderId());
        log.setActor(event.getActor());
        log.setDetails(event.getDetails());
        log.setOccurredAt(event.getCreatedAt());
        auditLogRepository.save(log);
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
