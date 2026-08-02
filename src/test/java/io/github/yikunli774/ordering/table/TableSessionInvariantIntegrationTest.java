package io.github.yikunli774.ordering.table;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the core session invariant enforced by the DB itself: a dining table
 * can have at most one active (OPEN/PENDING_PAYMENT) session at a time.
 */
class TableSessionInvariantIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aTableCanHaveAtMostOneActiveSession() {
        long tableId = jdbc.queryForObject(
                "SELECT id FROM dining_table WHERE code = 'T01'", Long.class);

        // First active session: fine.
        jdbc.update("INSERT INTO table_session (dining_table_id, status) VALUES (?, 'OPEN')", tableId);

        // Second active session on the same table: rejected by the unique active index.
        assertThatThrownBy(() ->
                jdbc.update("INSERT INTO table_session (dining_table_id, status) VALUES (?, 'OPEN')", tableId))
                .isInstanceOf(DuplicateKeyException.class);

        // Closing the first frees the table; a new active session is allowed again.
        jdbc.update("UPDATE table_session SET status = 'CLOSED' WHERE dining_table_id = ? AND status = 'OPEN'", tableId);
        jdbc.update("INSERT INTO table_session (dining_table_id, status) VALUES (?, 'OPEN')", tableId);

        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM table_session WHERE dining_table_id = ? AND status = 'OPEN'",
                Integer.class, tableId);
        assertThat(activeCount).isEqualTo(1);
    }
}
