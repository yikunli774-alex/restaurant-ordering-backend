package io.github.yikunli774.ordering.checkout;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class CheckoutRepository {

    private final JdbcTemplate jdbc;

    public CheckoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PaymentInfo(long id, String status, BigDecimal amount) {
    }

    public int billableRoundCount(long sessionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_round WHERE table_session_id = ? AND status <> 'CANCELLED'",
                Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    public BigDecimal billableAmount(long sessionId) {
        BigDecimal amount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM order_round WHERE table_session_id = ? AND status <> 'CANCELLED'",
                BigDecimal.class, sessionId);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** CAS OPEN -> PENDING_PAYMENT. Only one concurrent checkout can win. */
    public int startCheckout(long sessionId, BigDecimal billAmount) {
        return jdbc.update("""
                UPDATE table_session
                SET status = 'PENDING_PAYMENT', bill_amount = ?, version = version + 1
                WHERE id = ? AND status = 'OPEN'
                """, billAmount, sessionId);
    }

    /** CAS PENDING_PAYMENT -> CLOSED. Only one concurrent payment can win. */
    public int closeAsPaid(long sessionId) {
        return jdbc.update("""
                UPDATE table_session
                SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(6), version = version + 1
                WHERE id = ? AND status = 'PENDING_PAYMENT'
                """, sessionId);
    }

    public void insertPayment(long sessionId, BigDecimal amount) {
        jdbc.update("INSERT INTO payment (table_session_id, amount, status) VALUES (?, ?, 'PENDING')",
                sessionId, amount);
    }

    public void markPaymentSucceeded(long sessionId) {
        jdbc.update("""
                UPDATE payment SET status = 'SUCCEEDED', paid_at = CURRENT_TIMESTAMP(6)
                WHERE table_session_id = ? AND status = 'PENDING'
                """, sessionId);
    }

    public Optional<PaymentInfo> findLatestPayment(long sessionId) {
        return jdbc.query("""
                SELECT id, status, amount FROM payment
                WHERE table_session_id = ? ORDER BY id DESC LIMIT 1
                """,
                (rs, i) -> new PaymentInfo(rs.getLong("id"), rs.getString("status"), rs.getBigDecimal("amount")),
                sessionId).stream().findFirst();
    }
}
