package com.ecommerce.oms.auth;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.oms.support.IntegrationTestBase;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;

class AuthApiTest extends IntegrationTestBase {

    @Test
    void registerCreatesCustomerWithNormalizedEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email":"Alice@Example.com","password":"supersecret","fullName":"Alice"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        fixtures.customer("alice@example.com");
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email":"ALICE@example.com","password":"supersecret","fullName":"Alice"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void invalidPayloadReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email":"not-an-email","password":"short","fullName":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"oms\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        User alice = fixtures.customer("alice@example.com");
        mvc.perform(get("/api/auth/me").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        fixtures.customer("alice@example.com");
        mvc.perform(get("/api/auth/me").with(httpBasic("alice@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void wrongRoleIsForbiddenProblemDetail() throws Exception {
        User alice = fixtures.customer("alice@example.com");
        mvc.perform(get("/api/admin/users").with(as(alice)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }
}
