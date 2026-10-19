package io.github.seongeun308.poormall.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseCleaner {

    private static final String TABLES_QUERY = """
            SELECT tablename FROM pg_tables
            WHERE schemaname = 'public'
              AND tablename <> 'flyway_schema_history'
            """;

    private final JdbcTemplate jdbcTemplate;
    private List<String> tables;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        if (tables == null) {
            tables = jdbcTemplate.queryForList(TABLES_QUERY, String.class);
        }
        if (tables.isEmpty()) {
            return;
        }
        jdbcTemplate.execute(
                "TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }
}
