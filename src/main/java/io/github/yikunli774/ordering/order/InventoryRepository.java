package io.github.yikunli774.ordering.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {

    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically reserves stock. The conditional {@code WHERE available >= quantity}
     * means concurrent reservations can never oversell or drive stock negative — the
     * row lock serializes them and only those with enough stock succeed.
     * Returns false when there is not enough stock.
     */
    public boolean reserve(long menuItemId, int quantity, String operationId) {
        int rows = jdbc.update("""
                UPDATE inventory
                SET available = available - ?, reserved = reserved + ?, version = version + 1
                WHERE menu_item_id = ? AND available >= ?
                """, quantity, quantity, menuItemId, quantity);
        if (rows == 0) {
            return false;
        }
        jdbc.update("""
                INSERT INTO inventory_ledger (menu_item_id, delta, reason, operation_id)
                VALUES (?, ?, 'RESERVE', ?)
                """, menuItemId, -quantity, operationId);
        return true;
    }
}
