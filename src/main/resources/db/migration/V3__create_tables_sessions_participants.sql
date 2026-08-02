-- Dining tables, their ordering sessions, and anonymous participants.

CREATE TABLE dining_table (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_dining_table_code UNIQUE (store_id, code),
    CONSTRAINT fk_dining_table_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT ck_dining_table_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE table_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dining_table_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    bill_amount DECIMAL(10, 2) NULL,
    version INT NOT NULL DEFAULT 0,
    opened_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at TIMESTAMP(6) NULL,
    -- Automatically the table id while the session is active, NULL once it ends.
    -- With the UNIQUE index below, this enforces "at most one active session per
    -- table": MySQL permits many NULLs but not two equal non-NULL values.
    active_table_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN status IN ('OPEN', 'PENDING_PAYMENT') THEN dining_table_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_table_session_active UNIQUE (active_table_id),
    CONSTRAINT fk_table_session_table FOREIGN KEY (dining_table_id) REFERENCES dining_table (id),
    CONSTRAINT ck_table_session_status CHECK (status IN ('OPEN', 'PENDING_PAYMENT', 'CLOSED', 'FORCE_CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE participant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    table_session_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_participant_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_participant_session FOREIGN KEY (table_session_id) REFERENCES table_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed one demo store and two tables so the join flow has something to scan.
INSERT INTO store (code, name) VALUES ('S01', 'Demo Store');

INSERT INTO dining_table (store_id, code, name)
SELECT id, 'T01', 'Table 1' FROM store WHERE code = 'S01';

INSERT INTO dining_table (store_id, code, name)
SELECT id, 'T02', 'Table 2' FROM store WHERE code = 'S01';
