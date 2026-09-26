package com.ecommerce.oms.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.inventory.InventoryItem;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FulfillmentApiTest extends IntegrationTestBase {

    private User alice;
    private User staffW1;
    private User staffW2;
    private Product laptop;
    private Warehouse w1;

    @BeforeEach
    void setUp() {
        alice = fixtures.customer("alice@test.local");
        laptop = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-1", "Laptop", "100.00");
        w1 = fixtures.warehouse("W1");
        Warehouse w2 = fixtures.warehouse("W2");
        fixtures.stock(laptop, w1, 10, 0);
        staffW1 = fixtures.staff("staff1@test.local", w1);
        staffW2 = fixtures.staff("staff2@test.local", w2);
    }

    private long confirmedOrder(int quantity) throws Exception {
        fixtures.cartWith(alice, laptop, quantity);
        long orderId = checkout(alice);
        outboxProcessor.processBatch();
        return orderId;
    }

    private String statusBody(String status) {
        return "{\"status\":\"%s\",\"note\":\"by staff\"}".formatted(status);
    }

    @Test
    void staffMovesOrderThroughFulfillmentAndShippingCommitsStock() throws Exception {
        long orderId = confirmedOrder(2);
        String url = "/api/warehouse/orders/%d/status".formatted(orderId);

        mvc.perform(get("/api/warehouse/orders").with(as(staffW1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId));

        mvc.perform(patch(url).with(as(staffW1)).contentType(APPLICATION_JSON).content(statusBody("PACKED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PACKED"));
        mvc.perform(patch(url).with(as(staffW1)).contentType(APPLICATION_JSON).content(statusBody("SHIPPED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"));

        InventoryItem row = fixtures.inventory(laptop, w1);
        assertThat(row.getOnHand()).isEqualTo(8);
        assertThat(row.getReserved()).isZero();

        mvc.perform(patch(url).with(as(staffW1)).contentType(APPLICATION_JSON).content(statusBody("DELIVERED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty())
                .andExpect(jsonPath("$.history.length()").value(5));

        mvc.perform(get("/api/orders/" + orderId).with(as(alice))).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void stepsCannotBeSkippedAndStaffCannotCancel() throws Exception {
        long orderId = confirmedOrder(1);
        String url = "/api/warehouse/orders/%d/status".formatted(orderId);
        mvc.perform(patch(url).with(as(staffW1)).contentType(APPLICATION_JSON).content(statusBody("SHIPPED")))
                .andExpect(status().isConflict());
        mvc.perform(patch(url).with(as(staffW1)).contentType(APPLICATION_JSON).content(statusBody("CANCELLED")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(url).with(as(staffW1)).contentType(APPLICATION_JSON).content(statusBody("TELEPORTED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffOfAnotherWarehouseCannotSeeOrTouchTheOrder() throws Exception {
        long orderId = confirmedOrder(1);
        mvc.perform(patch("/api/warehouse/orders/%d/status".formatted(orderId)).with(as(staffW2))
                        .contentType(APPLICATION_JSON).content(statusBody("PACKED")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/warehouse/orders").with(as(staffW2))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void customersCannotUseWarehouseEndpointsButAdminsCan() throws Exception {
        long orderId = confirmedOrder(1);
        String url = "/api/warehouse/orders/%d/status".formatted(orderId);
        mvc.perform(patch(url).with(as(alice)).contentType(APPLICATION_JSON).content(statusBody("PACKED")))
                .andExpect(status().isForbidden());
        mvc.perform(patch(url).with(as(fixtures.admin())).contentType(APPLICATION_JSON).content(statusBody("PACKED")))
                .andExpect(status().isOk());
    }

    @Test
    void warehouseQueueRejectsUnknownSort() throws Exception {
        mvc.perform(get("/api/warehouse/orders").param("sort", "nope").with(as(staffW1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cannot sort by 'nope'; allowed: grandTotal, id, placedAt, status"));
        mvc.perform(get("/api/warehouse/orders").param("sort", "placedAt,desc").with(as(staffW1)))
                .andExpect(status().isOk());
    }

    @Test
    void advanceToDeliveredHelperWorks() throws Exception {
        fixtures.cartWith(alice, laptop, 1);
        long orderId = checkout(alice);
        advanceToDelivered(orderId);
        mvc.perform(get("/api/orders/" + orderId).with(as(alice))).andExpect(jsonPath("$.status").value("DELIVERED"));
    }
}
