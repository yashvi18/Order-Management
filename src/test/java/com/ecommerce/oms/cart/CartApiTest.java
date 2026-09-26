package com.ecommerce.oms.cart;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Category;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartApiTest extends IntegrationTestBase {

    private User alice;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        Category electronics = fixtures.category("Electronics", "0.18");
        laptop = fixtures.product(electronics, "LAP-1", "Laptop", "1000.00");
        mouse = fixtures.product(electronics, "MOU-1", "Mouse", "25.50");
        var w = fixtures.warehouse("W1");
        fixtures.stock(laptop, w, 5, 0);
        fixtures.stock(mouse, w, 50, 0);
    }

    private String addBody(Product product, int quantity) {
        return """
                {"productId":%d,"quantity":%d}
                """.formatted(product.getId(), quantity);
    }

    @Test
    void addingItemsMergesLinesAndComputesSubtotal() throws Exception {
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content(addBody(laptop, 1)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content(addBody(laptop, 1)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content(addBody(mouse, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.totalQuantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(2051.0));
    }

    @Test
    void cannotAddMoreThanAvailableStock() throws Exception {
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content(addBody(laptop, 6)))
                .andExpect(status().isConflict());
    }

    @Test
    void inactiveOrUnknownProductsCannotBeAdded() throws Exception {
        fixtures.deactivate(mouse);
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content(addBody(mouse, 1)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content("""
                        {"productId":99999,"quantity":1}
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidQuantitiesAreRejected() throws Exception {
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content(addBody(laptop, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cart/items").with(as(alice)).contentType(APPLICATION_JSON).content(addBody(mouse, 101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingToZeroRemovesTheLineAndClearEmptiesTheCart() throws Exception {
        fixtures.cartWith(alice, laptop, 1);
        fixtures.cartWith(alice, mouse, 1);
        mvc.perform(put("/api/cart/items/" + laptop.getId()).with(as(alice)).contentType(APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(put("/api/cart/items/" + laptop.getId()).with(as(alice)).contentType(APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart").with(as(alice))).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").with(as(alice))).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void cartsAreIsolatedPerCustomerAndClosedToOtherRoles() throws Exception {
        fixtures.cartWith(alice, laptop, 1);
        User bob = fixtures.customer("bob@test.local");
        mvc.perform(get("/api/cart").with(as(bob))).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/cart").with(as(fixtures.admin()))).andExpect(status().isForbidden());
    }

    @Test
    void deactivatedProductStaysVisibleButNotPurchasable() throws Exception {
        fixtures.cartWith(alice, mouse, 1);
        fixtures.deactivate(mouse);
        mvc.perform(get("/api/cart").with(as(alice)))
                .andExpect(jsonPath("$.items[0].purchasable").value(false));
    }
}
