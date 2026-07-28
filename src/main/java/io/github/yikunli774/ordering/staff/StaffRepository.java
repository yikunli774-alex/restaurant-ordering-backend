package io.github.yikunli774.ordering.staff;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Database access for staff accounts and their effective authorities.
 * Uses plain JdbcTemplate (simple, explicit SQL) for now.
 */
@Repository
public class StaffRepository {

    private final JdbcTemplate jdbc;

    public StaffRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Minimal projection needed to authenticate a login. */
    public record StaffAuth(long id, String username, String passwordHash, String status) {
    }

    public Optional<StaffAuth> findByUsername(String username) {
        return jdbc.query(
                        "SELECT id, username, password_hash, status FROM staff_account WHERE username = ?",
                        (rs, i) -> new StaffAuth(
                                rs.getLong("id"),
                                rs.getString("username"),
                                rs.getString("password_hash"),
                                rs.getString("status")),
                        username)
                .stream()
                .findFirst();
    }

    /** All authorities for a staff member: permission codes plus ROLE_-prefixed role codes. */
    public List<String> findAuthorities(long staffId) {
        return jdbc.queryForList("""
                SELECT p.code
                FROM staff_role sr
                JOIN role_permission rp ON rp.role_id = sr.role_id
                JOIN permission p ON p.id = rp.permission_id
                WHERE sr.staff_id = ?
                UNION
                SELECT CONCAT('ROLE_', r.code)
                FROM staff_role sr
                JOIN role r ON r.id = sr.role_id
                WHERE sr.staff_id = ?
                """, String.class, staffId, staffId);
    }

    public boolean usernameExists(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff_account WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public long insertStaff(String username, String passwordHash, String displayName) {
        jdbc.update(
                "INSERT INTO staff_account (username, password_hash, display_name) VALUES (?, ?, ?)",
                username, passwordHash, displayName);
        return jdbc.queryForObject(
                "SELECT id FROM staff_account WHERE username = ?", Long.class, username);
    }

    public void assignRoleByCode(long staffId, String roleCode) {
        jdbc.update("""
                INSERT IGNORE INTO staff_role (staff_id, role_id)
                SELECT ?, r.id FROM role r WHERE r.code = ?
                """, staffId, roleCode);
    }
}
