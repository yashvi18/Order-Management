package com.ecommerce.oms.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Truncates every table between tests so tests (including multi-threaded ones) never share state. */
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'",
                String.class);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : tables) {
            jdbc.execute("TRUNCATE TABLE \"" + table + "\" RESTART IDENTITY");
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
}
