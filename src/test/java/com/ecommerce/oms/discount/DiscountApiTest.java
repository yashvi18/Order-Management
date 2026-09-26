package com.ecommerce.oms.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DiscountApiTest extends IntegrationTestBase {

    @Autowired private DiscountService discountService;
    @Autowired private DiscountRepository discountRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = fixtures.admin();
    }

    @Test
    void adminCreatesDiscountWithUppercasedCode() throws Exception {
        mvc.perform(post("/api/admin/discounts").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"code":"welcome10","type":"PERCENTAGE","value":10,"maxDiscountAmount":500,"usageLimit":100}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WELCOME10"))
                .andExpect(jsonPath("$.timesUsed").value(0))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void duplicateAndInvalidDiscountsAreRejected() throws Exception {
        fixtures.discount("SAVE10", DiscountType.PERCENTAGE, "10", null);
        mvc.perform(post("/api/admin/discounts").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"code":"save10","type":"FIXED_AMOUNT","value":5}
                        """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/discounts").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"code":"HUGE","type":"PERCENTAGE","value":150}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/discounts").with(as(admin)).contentType(APPLICATION_JSON).content("""
                        {"code":"WINDOW","type":"FIXED_AMOUNT","value":5,
                         "validFrom":"2026-10-01T00:00:00Z","validUntil":"2026-09-01T00:00:00Z"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redeemEnforcesUsageLimitAtomically() {
        Discount once = fixtures.discount("ONCE", DiscountType.FIXED_AMOUNT, "5", 1);
        tx.executeWithoutResult(s -> discountService.redeem(once.getId()));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> discountService.redeem(once.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Discount code usage limit reached");
        assertThat(discountRepository.findById(once.getId()).orElseThrow().getTimesUsed()).isEqualTo(1);
    }
}
