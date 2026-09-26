package com.ecommerce.oms.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.order.OrderRepository;
import com.ecommerce.oms.order.OrderStatus;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"oms.outbox.scheduler-enabled=true", "oms.outbox.poll-interval-ms=100"})
class BackgroundSchedulerTest extends IntegrationTestBase {

    @Autowired private OrderRepository orderRepository;

    @Test
    void scheduledWorkerConfirmsOrdersInTheBackground() throws Exception {
        User alice = fixtures.customer("alice@test.local");
        Product laptop = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-1", "Laptop", "100.00");
        fixtures.stock(laptop, fixtures.warehouse("W1"), 5, 0);
        fixtures.cartWith(alice, laptop, 1);

        long orderId = checkout(alice);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED));
    }
}
