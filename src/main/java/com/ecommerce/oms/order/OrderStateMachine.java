package com.ecommerce.oms.order;

import static com.ecommerce.oms.order.OrderStatus.*;

import com.ecommerce.oms.common.ConflictException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Single source of truth for the fulfillment lifecycle. */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PLACED, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(PACKED, CANCELLED));
        ALLOWED.put(PACKED, EnumSet.of(SHIPPED));
        ALLOWED.put(SHIPPED, EnumSet.of(DELIVERED));
        ALLOWED.put(DELIVERED, EnumSet.of(PARTIALLY_RETURNED, RETURNED));
        ALLOWED.put(PARTIALLY_RETURNED, EnumSet.of(PARTIALLY_RETURNED, RETURNED));
        ALLOWED.put(RETURNED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void assertTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new ConflictException("Cannot move order from " + from + " to " + to);
        }
    }
}
