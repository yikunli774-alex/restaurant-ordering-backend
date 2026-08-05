-- One bill per table session, paid once at checkout (一客一结账). Payment is simulated,
-- but the table + status are the seam a real payment provider would plug into.

CREATE TABLE payment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    table_session_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    provider_ref VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    paid_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_session FOREIGN KEY (table_session_id) REFERENCES table_session (id),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
