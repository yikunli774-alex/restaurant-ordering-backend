package io.github.yikunli774.ordering.table;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class TableSessionRepository {

    private final JdbcTemplate jdbc;

    public TableSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DiningTable(long id, String code, String name, String status) {
    }

    public record TableRow(String code, String name) {
    }

    public record SessionView(long id, String tableCode, String status, BigDecimal billAmount) {
    }

    public Optional<DiningTable> findTableByCode(String code) {
        return jdbc.query(
                        "SELECT id, code, name, status FROM dining_table WHERE code = ?",
                        (rs, i) -> new DiningTable(
                                rs.getLong("id"), rs.getString("code"),
                                rs.getString("name"), rs.getString("status")),
                        code)
                .stream().findFirst();
    }

    public List<TableRow> findAllTables() {
        return jdbc.query(
                "SELECT code, name FROM dining_table ORDER BY code",
                (rs, i) -> new TableRow(rs.getString("code"), rs.getString("name")));
    }

    public Optional<Long> findActiveSession(long tableId) {
        return jdbc.query(
                        "SELECT id FROM table_session WHERE dining_table_id = ? "
                                + "AND status IN ('OPEN', 'PENDING_PAYMENT') LIMIT 1",
                        (rs, i) -> rs.getLong("id"), tableId)
                .stream().findFirst();
    }

    /**
     * Returns the table's active session, creating one if absent. Concurrency-safe:
     * if two callers race to create, the unique active index lets only one INSERT win;
     * the loser catches the duplicate and re-reads the winner's session.
     */
    public long findOrCreateActiveSession(long tableId) {
        Optional<Long> existing = findActiveSession(tableId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbc.update("INSERT INTO table_session (dining_table_id, status) VALUES (?, 'OPEN')", tableId);
        } catch (DuplicateKeyException lostTheRace) {
            // Someone else opened the session first; fall through and read it.
        }
        return findActiveSession(tableId)
                .orElseThrow(() -> new IllegalStateException("Active session missing after create"));
    }

    public long insertParticipant(long sessionId, String tokenHash) {
        jdbc.update("INSERT INTO participant (table_session_id, token_hash) VALUES (?, ?)", sessionId, tokenHash);
        return jdbc.queryForObject("SELECT id FROM participant WHERE token_hash = ?", Long.class, tokenHash);
    }

    public Optional<SessionView> findSession(long sessionId) {
        return jdbc.query("""
                        SELECT ts.id, dt.code AS table_code, ts.status, ts.bill_amount
                        FROM table_session ts
                        JOIN dining_table dt ON dt.id = ts.dining_table_id
                        WHERE ts.id = ?
                        """,
                        (rs, i) -> new SessionView(
                                rs.getLong("id"), rs.getString("table_code"),
                                rs.getString("status"), rs.getBigDecimal("bill_amount")),
                        sessionId)
                .stream().findFirst();
    }
}
