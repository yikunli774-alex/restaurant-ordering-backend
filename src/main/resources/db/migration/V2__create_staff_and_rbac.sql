-- Staff accounts and role-based access control (RBAC).
-- A staff member has roles; a role has permissions. Endpoints check permissions.

CREATE TABLE staff_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_staff_username UNIQUE (username),
    CONSTRAINT ck_staff_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(128) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permission_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Many-to-many: which roles a staff member has.
CREATE TABLE staff_role (
    staff_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (staff_id, role_id),
    CONSTRAINT fk_staff_role_staff FOREIGN KEY (staff_id) REFERENCES staff_account (id),
    CONSTRAINT fk_staff_role_role FOREIGN KEY (role_id) REFERENCES role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Many-to-many: which permissions a role grants.
CREATE TABLE role_permission (
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed the two roles.
INSERT INTO role (code, name) VALUES
    ('KITCHEN', 'Kitchen staff'),
    ('MANAGER', 'Manager');

-- Seed the permissions.
INSERT INTO permission (code, description) VALUES
    ('order:read', 'View kitchen orders'),
    ('order:prepare', 'Start preparing an order round'),
    ('order:ready', 'Mark an order round ready'),
    ('order:complete', 'Complete an order round'),
    ('session:close', 'Force-close a table session'),
    ('staff:manage', 'Manage staff accounts'),
    ('menu:manage', 'Manage menu items'),
    ('inventory:manage', 'Manage inventory');

-- KITCHEN gets the operational permissions.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN
    ('order:read', 'order:prepare', 'order:ready', 'order:complete', 'session:close')
WHERE r.code = 'KITCHEN';

-- MANAGER gets every permission.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p
WHERE r.code = 'MANAGER';
