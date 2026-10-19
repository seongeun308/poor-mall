package io.github.seongeun308.poormall.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class IntegrationTestSupportTest extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Testcontainers PostgreSQL에 Flyway V1이 적용된다")
    void flywayMigrationApplied() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    @DisplayName("테이블 비우기 후 모든 테이블이 비어 있다")
    void cleanerEmptiesTables() {
        Long walletId = jdbcTemplate.queryForObject(
                "INSERT INTO wallet (user_id, balance) VALUES (1, 1000) RETURNING id", Long.class);
        jdbcTemplate.update(
                "INSERT INTO wallet_entry (wallet_id, entry_type, amount, balance_after, idempotency_key) "
                        + "VALUES (?, 'SAVE', 1000, 1000, 'test-key')", walletId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM wallet_entry", Integer.class)).isOne();

        databaseCleaner.clean();

        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                        + "AND tablename <> 'flyway_schema_history'", String.class);

        assertThat(tables).contains("wallet", "wallet_entry");
        tables.forEach(table ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table, Integer.class)).isZero());
    }
}
