package com.ecommerce.oms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.events.OrderEventType;
import com.ecommerce.oms.events.OutboxProcessor;
import com.ecommerce.oms.events.OutboxRepository;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderTrackingApiTest extends IntegrationTestBase {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private OutboxProcessor outboxProcessor;

    private User alice;
    private User bob;
    private Product laptop;
    private Warehouse w1;

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        bob = fixtures.customer("bob@test.local");
        laptop = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-1", "Laptop", "100.00");
        w1 = fixtures.warehouse("W1");
        fixtures.stock(laptop, w1, 10, 0);
    }

    private long placeOrder(User customer, int quantity) throws Exception {
        fixtures.cartWith(customer, laptop, quantity);
        return checkout(customer);
    }

    @Test
    void customersSeeOnlyTheirOwnOrders() throws Exception {
        long aliceOrder = placeOrder(alice, 1);
        placeOrder(bob, 1);

        mvc.perform(get("/api/orders").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(aliceOrder))
                .andExpect(jsonPath("$.content[0].itemCount").value(1));
        mvc.perform(get("/api/orders/" + aliceOrder).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"));
        mvc.perform(get("/api/orders/" + aliceOrder).with(as(bob))).andExpect(status().isNotFound());
    }

    @Test
    void cancellingReleasesStockRefundsAndQueuesEvent() throws Exception {
        long orderId = placeOrder(alice, 2);
        mvc.perform(post("/api/orders/%d/cancel".formatted(orderId)).with(as(alice)).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.history[1].note").value("Changed my mind"));
        assertThat(fixtures.inventory(laptop, w1).getReserved()).isZero();
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId))
                .extracting(e -> e.getEventType())
                .containsExactly(OrderEventType.ORDER_PLACED, OrderEventType.ORDER_CANCELLED);
    }

    @Test
    void confirmedOrdersCanStillBeCancelledButPackedOnesCannot() throws Exception {
        long orderId = placeOrder(alice, 1);
        outboxProcessor.processBatch();
        mvc.perform(post("/api/orders/%d/cancel".formatted(orderId)).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long packedId = placeOrder(alice, 1);
        tx.executeWithoutResult(s -> {
            CustomerOrder order = orderRepository.findById(packedId).orElseThrow();
            order.transitionTo(OrderStatus.CONFIRMED, "test", null, Instant.now());
            order.transitionTo(OrderStatus.PACKED, "test", null, Instant.now());
        });
        mvc.perform(post("/api/orders/%d/cancel".formatted(packedId)).with(as(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot move order from PACKED to CANCELLED"));
    }

    @Test
    void cancellingAnotherCustomersOrderIs404() throws Exception {
        long aliceOrder = placeOrder(alice, 1);
        mvc.perform(post("/api/orders/%d/cancel".formatted(aliceOrder)).with(as(bob)))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.findById(aliceOrder).orElseThrow().getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void routerDoesNotConfirmAnOrderCancelledFirst() throws Exception {
        long orderId = placeOrder(alice, 1);
        mvc.perform(post("/api/orders/%d/cancel".formatted(orderId)).with(as(alice))).andExpect(status().isOk());
        outboxProcessor.processBatch();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void adminListsByStatusAndCanCancelAnyOrder() throws Exception {
        long aliceOrder = placeOrder(alice, 1);
        long bobOrder = placeOrder(bob, 1);
        User admin = fixtures.admin();
        mvc.perform(post("/api/admin/orders/%d/cancel".formatted(bobOrder)).with(as(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/orders").param("status", "PLACED").with(as(admin)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(aliceOrder));
        mvc.perform(get("/api/admin/orders").param("status", "NOPE").with(as(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/orders").with(as(alice))).andExpect(status().isForbidden());
    }
}
