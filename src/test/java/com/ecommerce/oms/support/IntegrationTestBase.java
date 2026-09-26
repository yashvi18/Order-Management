package com.ecommerce.oms.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.ecommerce.oms.auth.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestFixtures.class, DatabaseCleaner.class})
public abstract class IntegrationTestBase {

    @Autowired protected WebApplicationContext context;
    @Autowired protected TestFixtures fixtures;
    @Autowired protected TransactionTemplate tx;
    @Autowired private DatabaseCleaner databaseCleaner;

    protected MockMvc mvc;

    @BeforeEach
    void setUpBase() {
        databaseCleaner.clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected static RequestPostProcessor as(User user) {
        return httpBasic(user.getEmail(), TestFixtures.PASSWORD);
    }

    protected static long idFrom(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
