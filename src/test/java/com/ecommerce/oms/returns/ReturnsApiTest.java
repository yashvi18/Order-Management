package com.ecommerce.oms.returns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.order.OrderRepository;
import com.ecommerce.oms.payment.PaymentService;
import com.ecommerce.oms.payment.PaymentStatus;
import com.ecommerce.oms.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReturnsApiTest extends IntegrationTestBase {

    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentService paymentService;

    private User alice;
    private User staffW1;
    private Product pen;
    private Warehouse w1;

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        // 3.33 x 3 = 9.99; tax 5% = 0.50 (0.4995 rounded); line total 10.49 -> does not split evenly in 3.
        pen = fixtures.product(fixtures.category("Stationery", "0.05"), "PEN-1", "Pen", "3.33");
        w1 = fixtures.warehouse("W1");
        fixtures.stock(pen, w1, 10, 0);
        staffW1 = fixtures.staff("staff1@test.local", w1);
    }

    private long deliveredOrder(int quantity) throws Exception {
        fixtures.cartWith(alice, pen, quantity);
        long orderId = checkout(alice);
        advanceToDelivered(orderId);
        return orderId;
    }

    private long orderItemId(long orderId) {
        return tx.execute(s -> orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getId());
    }

    private long requestReturn(long orderId, long itemId, int quantity) throws Exception {
        return idFrom(mvc.perform(post("/api/orders/%d/returns".formatted(orderId)).with(as(alice))
                        .contentType(APPLICATION_JSON).content("""
                                {"reason":"Not needed","items":[{"orderItemId":%d,"quantity":%d}]}
                                """.formatted(itemId, quantity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn());
    }

    private void receive(long returnId, double expectedRefund) throws Exception {
        mvc.perform(post("/api/warehouse/returns/%d/receive".formatted(returnId)).with(as(staffW1))
                        .contentType(APPLICATION_JSON).content("{\"note\":\"Box intact\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.refundAmount").value(expectedRefund));
    }

    @Test
    void partialReturnRestocksRefundsAndMarksOrderPartiallyReturned() throws Exception {
        long orderId = deliveredOrder(3);
        long returnId = requestReturn(orderId, orderItemId(orderId), 1);

        mvc.perform(get("/api/warehouse/returns").with(as(staffW1)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(returnId));
        receive(returnId, 3.50);

        mvc.perform(get("/api/orders/" + orderId).with(as(alice)))
                .andExpect(jsonPath("$.status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.items[0].returnedQuantity").value(1))
                .andExpect(jsonPath("$.payment.status").value("PARTIALLY_REFUNDED"));
        assertThat(fixtures.inventory(pen, w1).getOnHand()).isEqualTo(8);
    }

    @Test
    void threeSingleUnitReturnsRefundExactlyTheLineTotal() throws Exception {
        long orderId = deliveredOrder(3);
        long itemId = orderItemId(orderId);
        receive(requestReturn(orderId, itemId, 1), 3.50);
        receive(requestReturn(orderId, itemId, 1), 3.50);
        receive(requestReturn(orderId, itemId, 1), 3.49);

        var payment = paymentService.findSummary(orderId).orElseThrow();
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refundedAmount()).isEqualByComparingTo("10.49");
        mvc.perform(get("/api/orders/" + orderId).with(as(alice)))
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.items[0].refundedAmount").value(10.49));
        assertThat(fixtures.inventory(pen, w1).getOnHand()).isEqualTo(10);
    }

    @Test
    void cannotReturnBeforeDelivery() throws Exception {
        fixtures.cartWith(alice, pen, 1);
        long orderId = checkout(alice);
        mvc.perform(post("/api/orders/%d/returns".formatted(orderId)).with(as(alice)).contentType(APPLICATION_JSON)
                        .content("""
                                {"reason":"x","items":[{"orderItemId":%d,"quantity":1}]}
                                """.formatted(orderItemId(orderId))))
                .andExpect(status().isConflict());
    }

    @Test
    void returnQuantityMustBePositive() throws Exception {
        long orderId = deliveredOrder(1);
        mvc.perform(post("/api/orders/%d/returns".formatted(orderId)).with(as(alice)).contentType(APPLICATION_JSON)
                        .content("""
                                {"reason":"x","items":[{"orderItemId":%d,"quantity":0}]}
                                """.formatted(orderItemId(orderId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void cannotReturnMoreThanReturnableIncludingPendingRequests() throws Exception {
        long orderId = deliveredOrder(3);
        long itemId = orderItemId(orderId);
        requestReturn(orderId, itemId, 2);
        mvc.perform(post("/api/orders/%d/returns".formatted(orderId)).with(as(alice)).contentType(APPLICATION_JSON)
                        .content("""
                                {"reason":"x","items":[{"orderItemId":%d,"quantity":2}]}
                                """.formatted(itemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cannot return 2 x PEN-1; only 1 returnable"));
    }

    @Test
    void returnWindowIsEnforced() throws Exception {
        long orderId = deliveredOrder(1);
        tx.executeWithoutResult(s -> orderRepository.findById(orderId).orElseThrow()
                .setDeliveredAt(Instant.now().minus(Duration.ofDays(31))));
        mvc.perform(post("/api/orders/%d/returns".formatted(orderId)).with(as(alice)).contentType(APPLICATION_JSON)
                        .content("""
                                {"reason":"x","items":[{"orderItemId":%d,"quantity":1}]}
                                """.formatted(orderItemId(orderId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The 30-day return window has expired"));
    }

    @Test
    void rejectedReturnFreesQuantityAndIssuesNoRefund() throws Exception {
        long orderId = deliveredOrder(1);
        long itemId = orderItemId(orderId);
        long returnId = requestReturn(orderId, itemId, 1);
        mvc.perform(post("/api/warehouse/returns/%d/reject".formatted(returnId)).with(as(staffW1))
                        .contentType(APPLICATION_JSON).content("{\"note\":\"Item damaged by customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(paymentService.findSummary(orderId).orElseThrow().status()).isEqualTo(PaymentStatus.CAPTURED);
        requestReturn(orderId, itemId, 1);
        mvc.perform(get("/api/orders/%d/returns".formatted(orderId)).with(as(alice)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void accessRulesAndDoubleReceive() throws Exception {
        long orderId = deliveredOrder(1);
        long itemId = orderItemId(orderId);
        User bob = fixtures.customer("bob@test.local");
        mvc.perform(post("/api/orders/%d/returns".formatted(orderId)).with(as(bob)).contentType(APPLICATION_JSON)
                        .content("""
                                {"reason":"x","items":[{"orderItemId":%d,"quantity":1}]}
                                """.formatted(itemId)))
                .andExpect(status().isNotFound());

        long returnId = requestReturn(orderId, itemId, 1);
        User staffW2 = fixtures.staff("staff2@test.local", fixtures.warehouse("W2"));
        mvc.perform(post("/api/warehouse/returns/%d/receive".formatted(returnId)).with(as(staffW2)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/warehouse/returns/%d/receive".formatted(returnId)).with(as(staffW1)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/warehouse/returns/%d/receive".formatted(returnId)).with(as(staffW1)))
                .andExpect(status().isConflict());
    }
}
