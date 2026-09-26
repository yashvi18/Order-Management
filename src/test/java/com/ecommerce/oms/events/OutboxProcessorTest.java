package com.ecommerce.oms.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OutboxProcessorTest extends IntegrationTestBase {

    @Autowired private OrderEventPublisher publisher;
    @Autowired private OutboxProcessor processor;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @Test
    void publishedEventIsProcessedIntoNotificationAndAudit() throws Exception {
        User alice = fixtures.customer("alice@test.local");
        User bob = fixtures.customer("bob@test.local");
        tx.executeWithoutResult(s -> publisher.publish(OrderEventType.ORDER_CANCELLED, 42L, alice.getId(),
                "alice@test.local", "Order ORD-1 cancelled"));

        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING));
        assertThat(notificationRepository.count()).isZero();

        assertThat(processor.processBatch()).isEqualTo(1);

        OutboxEvent event = outboxRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(auditLogRepository.findByOrderIdOrderByIdAsc(42L)).singleElement()
                .satisfies(a -> assertThat(a.getEventType()).isEqualTo(OrderEventType.ORDER_CANCELLED));

        mvc.perform(get("/api/notifications").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(42))
                .andExpect(jsonPath("$.content[0].message").value("Your order was cancelled. Order ORD-1 cancelled"));
        mvc.perform(get("/api/notifications").with(as(bob))).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/admin/audit-logs").param("orderId", "42").with(as(fixtures.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].actor").value("alice@test.local"));
    }

    @Test
    void processingTwiceDoesNotDuplicateWork() {
        User alice = fixtures.customer("alice@test.local");
        tx.executeWithoutResult(s -> publisher.publish(OrderEventType.ORDER_PLACED, 1L, alice.getId(), "a", "x"));
        processor.processBatch();
        assertThat(processor.processBatch()).isZero();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void notificationsRejectUnknownSort() throws Exception {
        User alice = fixtures.customer("alice@test.local");
        mvc.perform(get("/api/notifications").param("sort", "nope").with(as(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cannot sort by 'nope'; allowed: createdAt, id"));
        mvc.perform(get("/api/notifications").param("sort", "createdAt,desc").with(as(alice)))
                .andExpect(status().isOk());
    }
}
