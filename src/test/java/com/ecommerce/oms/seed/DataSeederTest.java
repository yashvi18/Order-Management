package com.ecommerce.oms.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.oms.auth.UserRepository;
import com.ecommerce.oms.catalog.ProductRepository;
import com.ecommerce.oms.discount.DiscountRepository;
import com.ecommerce.oms.inventory.InventoryItemRepository;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.TestPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "oms.seed.enabled=true")
class DataSeederTest extends IntegrationTestBase {

    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private DiscountRepository discountRepository;

    @Test
    void seedsDemoDataOnceAndDemoCredentialsWork() throws Exception {
        dataSeeder.run(new DefaultApplicationArguments());
        long users = userRepository.count();
        long products = productRepository.count();
        assertThat(users).isEqualTo(4);
        assertThat(products).isEqualTo(6);
        assertThat(inventoryItemRepository.count()).isEqualTo(12);
        assertThat(discountRepository.count()).isEqualTo(2);

        dataSeeder.run(new DefaultApplicationArguments());
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(productRepository.count()).isEqualTo(products);

        mvc.perform(get("/api/auth/me").with(httpBasic("admin@oms.local", "Admin@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
