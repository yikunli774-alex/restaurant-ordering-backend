package io.github.yikunli774.ordering.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared test cleanup. Deletes all per-visit data (sessions, participants, rounds,
 * items, ledger, idempotency) in foreign-key-safe order. Tests share one MySQL
 * container, so they reset this state in {@code @BeforeEach}. When a new table with
 * an FK into a table below is added, add its delete here (child rows first).
 */
public final class TestData {

    private TestData() {
    }

    public static void resetSessions(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM order_item");
        jdbc.update("DELETE FROM inventory_ledger");
        jdbc.update("DELETE FROM api_idempotency");
        jdbc.update("DELETE FROM order_round");
        jdbc.update("DELETE FROM participant");
        jdbc.update("DELETE FROM table_session");
    }
}
