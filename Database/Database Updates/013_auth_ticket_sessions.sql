-- Emulator-managed SSO TTL that is compatible with existing CMS deployments.
-- The CMS continues to write only users.auth_ticket. On first use, the emulator
-- stores a SHA-256 hash of that ticket here with a fixed deadline. Reusing the
-- same ticket never extends its deadline; a newly issued ticket has a new hash.
CREATE TABLE IF NOT EXISTS `users_auth_ticket_sessions` (
    `ticket_hash` CHAR(64) NOT NULL,
    `user_id` INT NOT NULL,
    `expires_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`ticket_hash`),
    KEY `idx_auth_ticket_session_user` (`user_id`),
    KEY `idx_auth_ticket_session_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
