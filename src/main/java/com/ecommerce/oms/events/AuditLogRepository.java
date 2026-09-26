package com.ecommerce.oms.events;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOrderIdOrderByIdAsc(Long orderId);
}
