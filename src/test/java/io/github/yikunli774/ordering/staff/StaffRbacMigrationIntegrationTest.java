package io.github.yikunli774.ordering.staff;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class StaffRbacMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsRolePermissionsForManagerAndKitchen() {
        // V2 seeded 8/5; V7 added order:cancel to both.
        assertThat(permissionCountForRole("MANAGER")).isEqualTo(9);
        assertThat(permissionCountForRole("KITCHEN")).isEqualTo(6);
    }

    private Integer permissionCountForRole(String roleCode) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM role_permission rp
                JOIN role r ON r.id = rp.role_id
                WHERE r.code = ?
                """, Integer.class, roleCode);
    }
}
