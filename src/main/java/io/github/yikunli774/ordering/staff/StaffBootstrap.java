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
 * Solves the chicken-and-egg problem of the first account: on startup, if the
 * manager username does not exist yet, create it with a hashed password and the
 * MANAGER role. Idempotent and safe when two instances start at once.
 */
@Component
public class StaffBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

    private final StaffRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String managerUsername;
    private final String managerPassword;

    public StaffBootstrap(
            StaffRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${security.bootstrap.manager-username}") String managerUsername,
            @Value("${security.bootstrap.manager-password}") String managerPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.managerUsername = managerUsername;
        this.managerPassword = managerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.usernameExists(managerUsername)) {
            return;
        }
        try {
            long id = repository.insertStaff(
                    managerUsername, passwordEncoder.encode(managerPassword), "Manager");
            repository.assignRoleByCode(id, "MANAGER");
            log.info("Bootstrapped initial manager account '{}'", managerUsername);
        } catch (DuplicateKeyException e) {
            // Another instance created it first; that is fine.
            log.info("Manager account '{}' already created by another instance", managerUsername);
        }
    }
}
