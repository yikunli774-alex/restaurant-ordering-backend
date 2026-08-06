package io.github.yikunli774.ordering.kitchen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KitchenRepository {

    private final JdbcTemplate jdbc;

    public KitchenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record QueuedRound(long id, int roundNo, String status, String tableCode) {
    }

    /** Rounds the kitchen still cares about (not completed/cancelled), oldest first. */
    public List<QueuedRound> queue() {
        return jdbc.query("""
                SELECT r.id, r.round_no, r.status, dt.code AS table_code
                FROM order_round r
                JOIN table_session ts ON ts.id = r.table_session_id
                JOIN dining_table dt ON dt.id = ts.dining_table_id
                WHERE r.status IN ('CONFIRMED', 'PREPARING', 'READY')
                ORDER BY r.created_at, r.id
                """,
                (rs, i) -> new QueuedRound(rs.getLong("id"), rs.getInt("round_no"),
                        rs.getString("status"), rs.getString("table_code")));
    }

    public Optional<String> findStatus(long roundId) {
        return jdbc.query("SELECT status FROM order_round WHERE id = ?",
                        (rs, i) -> rs.getString("status"), roundId)
                .stream().findFirst();
    }

    /** CAS one state to the next; returns 0 if the round was not in {@code from} state. */
    public int transition(long roundId, String from, String to) {
        return jdbc.update("UPDATE order_round SET status = ? WHERE id = ? AND status = ?", to, roundId, from);
    }
}
