package io.github.yikunli774.ordering.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RoundSummary(long id, int roundNo, String status, BigDecimal amount) {
    }

    public record RoundItem(long menuItemId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public Optional<Long> findIdempotentResult(String scope, String key) {
        return jdbc.query(
                        "SELECT result_id FROM api_idempotency WHERE scope = ? AND idempotency_key = ?",
                        (rs, i) -> rs.getLong("result_id"), scope, key)
                .stream().findFirst();
    }

    public void insertIdempotency(String scope, String key, long resultId) {
        jdbc.update("INSERT INTO api_idempotency (scope, idempotency_key, result_id) VALUES (?, ?, ?)",
                scope, key, resultId);
    }

    public int nextRoundNo(long sessionId) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(round_no), 0) + 1 FROM order_round WHERE table_session_id = ?",
                Integer.class, sessionId);
        return next == null ? 1 : next;
    }

    public long insertRound(long sessionId, int roundNo, BigDecimal amount) {
        jdbc.update("INSERT INTO order_round (table_session_id, round_no, amount) VALUES (?, ?, ?)",
                sessionId, roundNo, amount);
        return jdbc.queryForObject(
                "SELECT id FROM order_round WHERE table_session_id = ? AND round_no = ?",
                Long.class, sessionId, roundNo);
    }

    public void insertItem(long roundId, long menuItemId, String name,
                           BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        jdbc.update("""
                INSERT INTO order_item
                    (order_round_id, menu_item_id, name_snapshot, unit_price, quantity, line_total)
                VALUES (?, ?, ?, ?, ?, ?)
                """, roundId, menuItemId, name, unitPrice, quantity, lineTotal);
    }

    public Optional<RoundSummary> findRound(long roundId) {
        return jdbc.query(
                        "SELECT id, round_no, status, amount FROM order_round WHERE id = ?",
                        this::mapSummary, roundId)
                .stream().findFirst();
    }

    public List<RoundSummary> findRoundsForSession(long sessionId) {
        return jdbc.query(
                "SELECT id, round_no, status, amount FROM order_round WHERE table_session_id = ? ORDER BY round_no",
                this::mapSummary, sessionId);
    }

    public List<RoundItem> findItems(long roundId) {
        return jdbc.query("""
                SELECT menu_item_id, name_snapshot, unit_price, quantity, line_total
                FROM order_item WHERE order_round_id = ?
                """,
                (rs, i) -> new RoundItem(
                        rs.getLong("menu_item_id"), rs.getString("name_snapshot"),
                        rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total")),
                roundId);
    }

    private RoundSummary mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RoundSummary(rs.getLong("id"), rs.getInt("round_no"),
                rs.getString("status"), rs.getBigDecimal("amount"));
    }
}
