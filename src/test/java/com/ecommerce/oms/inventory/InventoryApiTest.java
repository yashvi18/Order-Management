package com.ecommerce.oms.inventory;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryApiTest extends IntegrationTestBase {

    private User admin;
    private Product laptop;

    @BeforeEach
    void setUp() {
        admin = fixtures.admin();
        laptop = fixtures.product(fixtures.category("Electronics", "0.18"), "LAP-1", "Laptop", "1000.00");
    }

    @Test
    void adminCreatesWarehouseSetsStockAndPublicSeesAvailability() throws Exception {
        long warehouseId = idFrom(mvc.perform(post("/api/admin/warehouses").with(as(admin))
                        .contentType(APPLICATION_JSON).content("""
                                {"code":"BLR-01","name":"Bangalore DC","city":"Bengaluru"}
                                """))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(put("/api/admin/inventory/warehouses/%d/products/%d".formatted(warehouseId, laptop.getId()))
                        .with(as(admin)).contentType(APPLICATION_JSON).content("""
                                {"onHand":25}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(25))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(25))
                .andExpect(jsonPath("$.warehouseCode").value("BLR-01"));

        mvc.perform(get("/api/admin/inventory").param("productId", laptop.getId().toString()).with(as(admin)))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/catalog/products/%d/availability".formatted(laptop.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(25))
                .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    void duplicateWarehouseCodeIsConflict() throws Exception {
        fixtures.warehouse("BLR-01");
        mvc.perform(post("/api/admin/warehouses").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"code":"BLR-01","name":"Again","city":"Bengaluru"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void setStockBelowReservedIsRejected() throws Exception {
        Warehouse w = fixtures.warehouse("W1");
        fixtures.stock(laptop, w, 10, 4);
        String url = "/api/admin/inventory/warehouses/%d/products/%d".formatted(w.getId(), laptop.getId());
        mvc.perform(put(url).with(as(admin)).contentType(APPLICATION_JSON).content("{\"onHand\":3}"))
                .andExpect(status().isConflict());
        mvc.perform(put(url).with(as(admin)).contentType(APPLICATION_JSON).content("{\"onHand\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void adjustBelowReservedIsRejected() throws Exception {
        Warehouse w = fixtures.warehouse("W1");
        fixtures.stock(laptop, w, 10, 4);
        String url = "/api/admin/inventory/warehouses/%d/products/%d/adjustments".formatted(w.getId(), laptop.getId());
        mvc.perform(post(url).with(as(admin)).contentType(APPLICATION_JSON).content("{\"delta\":-7}"))
                .andExpect(status().isConflict());
        mvc.perform(post(url).with(as(admin)).contentType(APPLICATION_JSON).content("{\"delta\":-6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(4));
    }

    @Test
    void availabilityIgnoresInactiveWarehouses() throws Exception {
        fixtures.stock(laptop, fixtures.warehouse("W1"), 10, 0);
        fixtures.stock(laptop, fixtures.deactivate(fixtures.warehouse("W2")), 5, 0);
        mvc.perform(get("/api/catalog/products/%d/availability".formatted(laptop.getId())))
                .andExpect(jsonPath("$.available").value(10));
    }

    @Test
    void inventoryListingNeedsAFilter() throws Exception {
        mvc.perform(get("/api/admin/inventory").with(as(admin))).andExpect(status().isBadRequest());
    }

    @Test
    void staffAccountsRequireAnExistingWarehouse() throws Exception {
        Warehouse w = fixtures.warehouse("W1");
        mvc.perform(post("/api/admin/users").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"email":"s@test.local","password":"password123","fullName":"Sam","role":"WAREHOUSE_STAFF"}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"email":"s@test.local","password":"password123","fullName":"Sam","role":"WAREHOUSE_STAFF","warehouseId":%d}
                        """.formatted(w.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseId").value(w.getId()));
    }

    @Test
    void customersCannotManageWarehouses() throws Exception {
        mvc.perform(get("/api/admin/warehouses").with(as(fixtures.customer("c@test.local"))))
                .andExpect(status().isForbidden());
    }
}
