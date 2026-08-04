-- Order rounds (加菜): each cart submission becomes one round that goes to the kitchen.
-- Payment is deferred to checkout, so a round starts CONFIRMED.

CREATE TABLE order_round (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    table_session_id BIGINT UNSIGNED NOT NULL,
    round_no INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_order_round UNIQUE (table_session_id, round_no),
    CONSTRAINT fk_order_round_session FOREIGN KEY (table_session_id) REFERENCES table_session (id),
    CONSTRAINT ck_order_round_status
        CHECK (status IN ('CONFIRMED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Each item snapshots name + unit price at submission, so later menu changes never alter a past round.
CREATE TABLE order_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_round_id BIGINT UNSIGNED NOT NULL,
    menu_item_id BIGINT UNSIGNED NOT NULL,
    name_snapshot VARCHAR(100) NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_round FOREIGN KEY (order_round_id) REFERENCES order_round (id),
    CONSTRAINT fk_order_item_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_item (id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Auditable stock movements; unique operation_id makes each reserve/release idempotent.
CREATE TABLE inventory_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    menu_item_id BIGINT UNSIGNED NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(16) NOT NULL,
    operation_id VARCHAR(96) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_ledger_op UNIQUE (operation_id),
    CONSTRAINT fk_inventory_ledger_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_item (id),
    CONSTRAINT ck_inventory_ledger_reason CHECK (reason IN ('RESERVE', 'RELEASE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Durable idempotency: one (scope, key) maps to one created result id.
CREATE TABLE api_idempotency (
    scope VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    result_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (scope, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
