package com.ecommerce.oms.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class OpenApiTest extends IntegrationTestBase {

    @Test
    void apiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("E-commerce Order Management API"))
                .andExpect(jsonPath("$.paths['/api/checkout']").exists());
    }
}
