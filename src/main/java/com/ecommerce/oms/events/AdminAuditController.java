package com.ecommerce.oms.events;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

    public record AuditLogResponse(Long id, OrderEventType eventType, Long orderId, String actor, String details,
            Instant occurredAt, Instant recordedAt) {
        static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getEventType(), a.getOrderId(), a.getActor(), a.getDetails(),
                    a.getOccurredAt(), a.getRecordedAt());
        }
    }

    private final AuditLogRepository auditLogRepository;

    public AdminAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** With orderId: that order's full trail. Without: the latest 100 entries. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditLogResponse> list(@RequestParam(required = false) Long orderId) {
        if (orderId != null) {
            return auditLogRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(AuditLogResponse::from).toList();
        }
        return auditLogRepository.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id")))
                .map(AuditLogResponse::from).getContent();
    }
}
