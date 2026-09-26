package com.ecommerce.oms.order;

import static com.ecommerce.oms.order.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.oms.common.ConflictException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStateMachineTest {

    @ParameterizedTest
    @CsvSource({"PLACED,CONFIRMED", "PLACED,CANCELLED", "CONFIRMED,PACKED", "CONFIRMED,CANCELLED",
            "PACKED,SHIPPED", "SHIPPED,DELIVERED", "DELIVERED,PARTIALLY_RETURNED", "DELIVERED,RETURNED",
            "PARTIALLY_RETURNED,PARTIALLY_RETURNED", "PARTIALLY_RETURNED,RETURNED"})
    void allowsLifecycleTransitions(OrderStatus from, OrderStatus to) {
        assertThat(OrderStateMachine.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"PLACED,PACKED", "CONFIRMED,SHIPPED", "PACKED,CANCELLED", "SHIPPED,CANCELLED",
            "DELIVERED,CANCELLED", "CANCELLED,CONFIRMED", "RETURNED,DELIVERED", "SHIPPED,PACKED", "PLACED,PLACED"})
    void rejectsEverythingElse(OrderStatus from, OrderStatus to) {
        assertThat(OrderStateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> OrderStateMachine.assertTransition(from, to))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cannot move order from " + from + " to " + to);
    }
}
