CREATE TABLE IF NOT EXISTS session_recovery_tickets (
    user_id INT NOT NULL,
    token_hash BINARY(32) NOT NULL,
    issued_at TIMESTAMP(3) NOT NULL,
    recoverable_until TIMESTAMP(3) NULL,
    consumed_at TIMESTAMP(3) NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_session_recovery_token_hash (token_hash),
    KEY idx_session_recovery_expiry (recoverable_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
