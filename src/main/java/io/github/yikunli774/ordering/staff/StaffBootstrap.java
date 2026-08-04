package io.github.yikunli774.ordering.staff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the initial staff accounts on startup (a MANAGER and a KITCHEN account)
 * if they do not exist yet, so the system is usable and RBAC can be demonstrated.
 * Idempotent and safe when two instances start at once.
 */
@Component
public class StaffBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

    private final StaffRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String managerUsername;
    private final String managerPassword;
    private final String kitchenUsername;
    private final String kitchenPassword;

    public StaffBootstrap(
            StaffRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${security.bootstrap.manager-username}") String managerUsername,
            @Value("${security.bootstrap.manager-password}") String managerPassword,
            @Value("${security.bootstrap.kitchen-username}") String kitchenUsername,
            @Value("${security.bootstrap.kitchen-password}") String kitchenPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.managerUsername = managerUsername;
        this.managerPassword = managerPassword;
        this.kitchenUsername = kitchenUsername;
        this.kitchenPassword = kitchenPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent(managerUsername, managerPassword, "Manager", "MANAGER");
        createIfAbsent(kitchenUsername, kitchenPassword, "Kitchen", "KITCHEN");
    }

    private void createIfAbsent(String username, String password, String displayName, String roleCode) {
        if (repository.usernameExists(username)) {
            return;
        }
        try {
            long id = repository.insertStaff(username, passwordEncoder.encode(password), displayName);
            repository.assignRoleByCode(id, roleCode);
            log.info("Bootstrapped {} account '{}'", roleCode, username);
        } catch (DuplicateKeyException e) {
            log.info("Account '{}' already created by another instance", username);
        }
    }
}
