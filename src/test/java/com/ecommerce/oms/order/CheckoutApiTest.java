package com.ecommerce.oms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.discount.DiscountRepository;
import com.ecommerce.oms.discount.DiscountType;
import com.ecommerce.oms.events.OrderEventType;
import com.ecommerce.oms.events.OutboxRepository;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CheckoutApiTest extends IntegrationTestBase {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private DiscountRepository discountRepository;

    private User alice;
    private Product laptop;
    private Product book;
    private Warehouse w1;

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        laptop = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-1", "Laptop", "100.00");
        book = fixtures.product(fixtures.category("Books", "0.05"), "BK-1", "Novel", "50.00");
        w1 = fixtures.warehouse("W1");
        fixtures.stock(laptop, w1, 10, 0);
        fixtures.stock(book, w1, 10, 0);
    }

    private String checkoutJson(String discountCode, String token) {
        return """
                {"shippingAddress":{"line1":"12 MG Road","city":"Bengaluru","state":"KA","postalCode":"560001","country":"IN"},
                 "paymentToken":"%s"%s}
                """.formatted(token, discountCode == null ? "" : ",\"discountCode\":\"" + discountCode + "\"");
    }

    @Test
    void checkoutReservesStockChargesPaymentClearsCartAndQueuesEvent() throws Exception {
        fixtures.discount("SAVE10", DiscountType.PERCENTAGE, "10", null);
        fixtures.cartWith(alice, laptop, 2);
        fixtures.cartWith(alice, book, 1);

        long orderId = idFrom(mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON)
                        .content(checkoutJson("save10", "tok_visa")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/orders/")))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.subtotal").value(250.0))
                .andExpect(jsonPath("$.discountTotal").value(25.0))
                .andExpect(jsonPath("$.taxTotal").value(34.65))
                .andExpect(jsonPath("$.grandTotal").value(259.65))
                .andExpect(jsonPath("$.discountCode").value("SAVE10"))
                .andExpect(jsonPath("$.items[0].allocations[0].warehouseCode").value("W1"))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                .andExpect(jsonPath("$.payment.amount").value(259.65))
                .andExpect(jsonPath("$.history[0].toStatus").value("PLACED"))
                .andReturn());

        assertThat(fixtures.inventory(laptop, w1).getReserved()).isEqualTo(2);
        assertThat(fixtures.inventory(book, w1).getReserved()).isEqualTo(1);
        mvc.perform(get("/api/cart").with(as(alice))).andExpect(jsonPath("$.items.length()").value(0));
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId)).singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo(OrderEventType.ORDER_PLACED));
        assertThat(discountRepository.findByCodeIgnoreCase("SAVE10").orElseThrow().getTimesUsed()).isEqualTo(1);
    }

    @Test
    void lowercaseCountryCodeIsNormalised() throws Exception {
        fixtures.cartWith(alice, laptop, 1);
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON)
                        .content(checkoutJson(null, "tok_visa").replace("\"country\":\"IN\"", "\"country\":\"in\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingAddress.country").value("IN"));
    }

    @Test
    void emptyCartIsBadRequest() throws Exception {
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON).content(CHECKOUT_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cart is empty"));
    }

    @Test
    void invalidAddressIsBadRequest() throws Exception {
        fixtures.cartWith(alice, laptop, 1);
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON).content("""
                        {"shippingAddress":{"line1":"","city":"B","state":"KA","postalCode":"1","country":"india"},
                         "paymentToken":"tok_visa"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['shippingAddress.country']").exists());
    }

    @Test
    void insufficientStockRollsBackEverything() throws Exception {
        fixtures.cartWith(alice, laptop, 2);
        fixtures.cartWith(alice, book, 11);
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON).content(CHECKOUT_JSON))
                .andExpect(status().isConflict());
        assertThat(orderRepository.count()).isZero();
        assertThat(fixtures.inventory(laptop, w1).getReserved()).isZero();
        mvc.perform(get("/api/cart").with(as(alice))).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void declinedPaymentRollsBackReservationDiscountAndCart() throws Exception {
        fixtures.discount("SAVE10", DiscountType.PERCENTAGE, "10", null);
        fixtures.cartWith(alice, laptop, 1);
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON)
                        .content(checkoutJson("SAVE10", "tok_declined")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.detail").value("Payment declined: Card declined"));
        assertThat(orderRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
        assertThat(fixtures.inventory(laptop, w1).getReserved()).isZero();
        assertThat(discountRepository.findByCodeIgnoreCase("SAVE10").orElseThrow().getTimesUsed()).isZero();
        mvc.perform(get("/api/cart").with(as(alice))).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void exhaustedDiscountRollsBackCheckout() throws Exception {
        fixtures.discount("ONCE", DiscountType.FIXED_AMOUNT, "5.00", 1);
        User bob = fixtures.customer("bob@test.local");
        fixtures.cartWith(alice, laptop, 1);
        fixtures.cartWith(bob, laptop, 1);
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON)
                        .content(checkoutJson("ONCE", "tok_visa")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/checkout").with(as(bob)).contentType(APPLICATION_JSON)
                        .content(checkoutJson("ONCE", "tok_visa")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Discount code usage limit reached"));
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(fixtures.inventory(laptop, w1).getReserved()).isEqualTo(1);
    }

    @Test
    void idempotencyKeyReplaysTheOriginalOrder() throws Exception {
        fixtures.cartWith(alice, laptop, 1);
        long first = idFrom(mvc.perform(post("/api/checkout").with(as(alice)).header("Idempotency-Key", "abc-123")
                        .contentType(APPLICATION_JSON).content(CHECKOUT_JSON))
                .andExpect(status().isCreated()).andReturn());
        long second = idFrom(mvc.perform(post("/api/checkout").with(as(alice)).header("Idempotency-Key", "abc-123")
                        .contentType(APPLICATION_JSON).content(CHECKOUT_JSON))
                .andExpect(status().isOk()).andReturn());
        assertThat(second).isEqualTo(first);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void lineIsSplitAcrossWarehousesWhenNoSingleOneHasEnough() throws Exception {
        Warehouse w2 = fixtures.warehouse("W2");
        Product phone = fixtures.product(fixtures.category("Phones", "0.18"), "PH-1", "Phone", "10.00");
        fixtures.stock(phone, w1, 1, 0);
        fixtures.stock(phone, w2, 2, 0);
        fixtures.cartWith(alice, phone, 3);
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON).content(CHECKOUT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].allocations.length()").value(2))
                .andExpect(jsonPath("$.items[0].allocations[0].warehouseCode").value("W2"))
                .andExpect(jsonPath("$.items[0].allocations[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].allocations[1].warehouseCode").value("W1"));
    }

    @Test
    void productDeactivatedAfterAddingToCartCannotBeBought() throws Exception {
        fixtures.cartWith(alice, laptop, 1);
        fixtures.deactivate(laptop);
        mvc.perform(post("/api/checkout").with(as(alice)).contentType(APPLICATION_JSON).content(CHECKOUT_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Product LAP-1 is no longer available"));
    }
}
