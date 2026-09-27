package com.ecommerce.oms.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(OrderClockTest.FixedClockConfig.class)
class OrderClockTest extends IntegrationTestBase {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired private OrderRepository orderRepository;

    @Test
    void placedAndDeliveredTimestampsComeFromTheInjectedClock() throws Exception {
        User customer = fixtures.customer("clock@test.local");
        Product laptop = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-CLK", "Laptop", "100.00");
        Warehouse w1 = fixtures.warehouse("W-CLK");
        fixtures.stock(laptop, w1, 10, 0);
        fixtures.cartWith(customer, laptop, 1);

        long orderId = checkout(customer);
        advanceToDelivered(orderId);

        tx.executeWithoutResult(status -> {
            CustomerOrder order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getPlacedAt()).isEqualTo(FIXED_INSTANT);
            assertThat(order.getDeliveredAt()).isEqualTo(FIXED_INSTANT);
            assertThat(order.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
            assertThat(order.getHistory()).isNotEmpty();
            order.getHistory().forEach(h -> assertThat(h.getChangedAt()).isEqualTo(FIXED_INSTANT));
        });
    }
}
