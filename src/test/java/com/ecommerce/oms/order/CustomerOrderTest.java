package com.ecommerce.oms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.oms.common.ConflictException;
import org.junit.jupiter.api.Test;

class CustomerOrderTest {

    @Test
    void transitionsAreRecordedInHistory() {
        CustomerOrder order = new CustomerOrder();
        order.markPlaced("alice@test.local");
        order.transitionTo(OrderStatus.CONFIRMED, "system", "routed");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPlacedAt()).isNotNull();
        assertThat(order.getHistory()).hasSize(2);
        OrderStatusHistory last = order.getHistory().get(1);
        assertThat(last.getFromStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(last.getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(last.getActor()).isEqualTo("system");
        assertThat(last.getCustomerOrder()).isSameAs(order);
    }

    @Test
    void deliveredSetsDeliveredAt() {
        CustomerOrder order = new CustomerOrder();
        order.markPlaced("a");
        order.transitionTo(OrderStatus.CONFIRMED, "s", null);
        order.transitionTo(OrderStatus.PACKED, "s", null);
        order.transitionTo(OrderStatus.SHIPPED, "s", null);
        assertThat(order.getDeliveredAt()).isNull();
        order.transitionTo(OrderStatus.DELIVERED, "s", null);
        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    void illegalTransitionLeavesOrderUnchanged() {
        CustomerOrder order = new CustomerOrder();
        order.markPlaced("a");
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.SHIPPED, "s", null))
                .isInstanceOf(ConflictException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getHistory()).hasSize(1);
    }

    @Test
    void orderNumbersAreUniqueAndFormatted() {
        String a = OrderNumbers.next();
        String b = OrderNumbers.next();
        assertThat(a).matches("ORD-\\d{8}-[A-Z0-9]{8}");
        assertThat(a).isNotEqualTo(b);
    }
}
