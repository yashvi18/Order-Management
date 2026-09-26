package com.ecommerce.oms.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.events.AuditLogRepository;
import com.ecommerce.oms.events.NotificationRepository;
import com.ecommerce.oms.events.OutboxProcessor;
import com.ecommerce.oms.order.CustomerOrder;
import com.ecommerce.oms.order.OrderRepository;
import com.ecommerce.oms.order.OrderStatus;
import com.ecommerce.oms.order.OrderStatusHistory;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AsyncPipelineTest extends IntegrationTestBase {

    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private OrderRepository orderRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @Test
    void checkoutReturnsBeforeDownstreamWorkThenPipelineConfirmsNotifiesAndAudits() throws Exception {
        User alice = fixtures.customer("alice@test.local");
        Product laptop = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-1", "Laptop", "100.00");
        fixtures.stock(laptop, fixtures.warehouse("BLR-01"), 5, 0);
        fixtures.cartWith(alice, laptop, 1);

        long orderId = checkout(alice);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(notificationRepository.count()).isZero();

        assertThat(outboxProcessor.processBatch()).isEqualTo(1);   // ORDER_PLACED -> routing + notify + audit
        assertThat(outboxProcessor.processBatch()).isEqualTo(1);   // ORDER_STATUS_CHANGED -> notify + audit

        tx.executeWithoutResult(s -> {
            CustomerOrder order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            OrderStatusHistory last = order.getHistory().getLast();
            assertThat(last.getActor()).isEqualTo("system:fulfillment-router");
            assertThat(last.getNote()).contains("BLR-01");
        });
        assertThat(notificationRepository.count()).isEqualTo(2);
        assertThat(auditLogRepository.findByOrderIdOrderByIdAsc(orderId)).hasSize(2);
    }
}
