package com.ecommerce.oms.catalog;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogApiTest extends IntegrationTestBase {

    private User admin;

    @BeforeEach
    void setUp() {
        admin = fixtures.admin();
    }

    @Test
    void adminCreatesCategoryAndProduct() throws Exception {
        long categoryId = idFrom(mvc.perform(post("/api/admin/categories").with(as(admin))
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"Electronics","description":"Gadgets","taxRate":0.18}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andReturn());

        mvc.perform(post("/api/admin/products").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"sku":"lap-001","name":"Laptop","description":"14 inch","price":1499.99,"categoryId":%d}
                        """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("LAP-001"))
                .andExpect(jsonPath("$.categoryName").value("Electronics"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void customerCannotManageCatalogAndAnonymousMustAuthenticate() throws Exception {
        User customer = fixtures.customer("c@test.local");
        String body = """
                {"name":"Books","taxRate":0.05}
                """;
        mvc.perform(post("/api/admin/categories").with(as(customer)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/categories").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateSkuIsConflict() throws Exception {
        Category electronics = fixtures.category("Electronics", "0.18");
        fixtures.product(electronics, "LAP-001", "Laptop", "1000.00");
        mvc.perform(post("/api/admin/products").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"sku":"lap-001","name":"Other","price":10.00,"categoryId":%d}
                        """.formatted(electronics.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidProductIsRejected() throws Exception {
        Category electronics = fixtures.category("Electronics", "0.18");
        mvc.perform(post("/api/admin/products").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"sku":"X-1","name":"","price":0,"categoryId":%d}
                        """.formatted(electronics.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    void publicSearchFiltersAndHidesInactiveProducts() throws Exception {
        Category electronics = fixtures.category("Electronics", "0.18");
        Category books = fixtures.category("Books", "0.05");
        fixtures.product(electronics, "LAP-1", "Laptop Pro", "1500.00");
        fixtures.product(electronics, "PHN-1", "Phone", "800.00");
        fixtures.product(books, "BK-1", "Novel", "20.00");
        Product old = fixtures.deactivate(fixtures.product(electronics, "PHN-0", "Old Phone", "100.00"));

        mvc.perform(get("/api/catalog/products").param("categoryId", electronics.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/catalog/products").param("q", "PHONE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("PHN-1"));
        mvc.perform(get("/api/catalog/products").param("minPrice", "500").param("maxPrice", "1000"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/catalog/products").param("sort", "price,desc"))
                .andExpect(jsonPath("$.content[0].sku").value("LAP-1"));
        mvc.perform(get("/api/catalog/products/" + old.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/products/" + old.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void badSearchParametersAreRejected() throws Exception {
        mvc.perform(get("/api/catalog/products").param("sort", "bogus")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/products").param("minPrice", "100").param("maxPrice", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingCategoryWithProductsIsConflictAndDeletingProductDeactivates() throws Exception {
        Category electronics = fixtures.category("Electronics", "0.18");
        Product phone = fixtures.product(electronics, "PHN-1", "Phone", "800.00");
        mvc.perform(delete("/api/admin/categories/" + electronics.getId()).with(as(admin)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/products/" + phone.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/catalog/products/" + phone.getId())).andExpect(status().isNotFound());
    }
}
