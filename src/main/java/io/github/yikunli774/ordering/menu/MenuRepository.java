package io.github.yikunli774.ordering.menu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class MenuRepository {

    private final JdbcTemplate jdbc;

    public MenuRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** What a customer sees: everything not delisted, with a sold-out flag. */
    public record MenuItemView(long id, String code, String name, String category,
                               BigDecimal price, boolean soldOut) {
    }

    /** What staff sees: full status plus current stock. */
    public record MenuItemAdminView(long id, String code, String name, String category,
                                    BigDecimal price, String status, int available) {
    }

    public List<MenuItemView> findForCustomer() {
        return jdbc.query("""
                SELECT id, code, name, category, price, status
                FROM menu_item
                WHERE status <> 'DELISTED'
                ORDER BY category, code
                """,
                (rs, i) -> new MenuItemView(
                        rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("category"), rs.getBigDecimal("price"),
                        "SOLD_OUT".equals(rs.getString("status"))));
    }

    public List<MenuItemAdminView> findForStaff() {
        return jdbc.query(adminSelect() + " ORDER BY m.category, m.code", this::mapAdmin);
    }

    public Optional<MenuItemAdminView> findAdminById(long id) {
        return jdbc.query(adminSelect() + " WHERE m.id = ?", this::mapAdmin, id).stream().findFirst();
    }

    public long defaultStoreId() {
        return jdbc.queryForObject("SELECT MIN(id) FROM store", Long.class);
    }

    public long createMenuItem(long storeId, String code, String name, String category, BigDecimal price) {
        jdbc.update("INSERT INTO menu_item (store_id, code, name, category, price) VALUES (?, ?, ?, ?, ?)",
                storeId, code, name, category, price);
        return jdbc.queryForObject(
                "SELECT id FROM menu_item WHERE store_id = ? AND code = ?", Long.class, storeId, code);
    }

    public void createInventory(long menuItemId, int available) {
        jdbc.update("INSERT INTO inventory (menu_item_id, available) VALUES (?, ?)", menuItemId, available);
    }

    public void updateMenuItem(long id, BigDecimal price, String status) {
        jdbc.update("UPDATE menu_item SET price = ?, status = ?, version = version + 1 WHERE id = ?",
                price, status, id);
    }

    public int setInventoryAvailable(long menuItemId, int available) {
        return jdbc.update(
                "UPDATE inventory SET available = ?, version = version + 1 WHERE menu_item_id = ?",
                available, menuItemId);
    }

    private String adminSelect() {
        return """
                SELECT m.id, m.code, m.name, m.category, m.price, m.status,
                       COALESCE(inv.available, 0) AS available
                FROM menu_item m
                LEFT JOIN inventory inv ON inv.menu_item_id = m.id
                """;
    }

    private MenuItemAdminView mapAdmin(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new MenuItemAdminView(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("category"), rs.getBigDecimal("price"),
                rs.getString("status"), rs.getInt("available"));
    }
}
