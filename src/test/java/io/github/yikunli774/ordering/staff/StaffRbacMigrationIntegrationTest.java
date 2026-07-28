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
        assertThat(permissionCountForRole("MANAGER")).isEqualTo(8);
        assertThat(permissionCountForRole("KITCHEN")).isEqualTo(5);
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
