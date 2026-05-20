-- ============================================================================
-- 020_auth_ticket_ttl.sql
--
-- Adds an explicit expiry timestamp to the SSO auth_ticket on `users`.
--
-- The CMS issuing the ticket is expected to populate auth_ticket_expires_at
-- (e.g. NOW() + INTERVAL 60 SECOND) on every login redirect. The emulator-
-- side SELECT queries that look up a user by auth_ticket have been changed to
--
--     WHERE auth_ticket = ?
--       AND (auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW())
--
-- The NULL branch keeps backward-compatibility with CMS deployments that do
-- not populate the column yet: existing rows continue to authenticate the
-- same way they always did, and the TTL kicks in only once the CMS starts
-- writing the expiry value.
--
-- Idempotent: skips the ALTER if the column already exists.
-- ============================================================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'auth_ticket_expires_at'
);

SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `users` ADD COLUMN `auth_ticket_expires_at` TIMESTAMP NULL DEFAULT NULL AFTER `auth_ticket`',
    'SELECT ''auth_ticket_expires_at already present, skipping'' AS info'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


UPDATE emulator_settings SET `key`='ws.whitelist' WHERE  `key`='websockets.whitelist';
UPDATE emulator_settings SET `key`='ws.host' WHERE  `key`='ws.nitro.host';
UPDATE emulator_settings SET `key`='ws.port' WHERE  `key`='ws.nitro.port';
INSERT IGNORE INTO emulator_settings (`key`, `value`)
VALUES ('ws.ip.header', 'X-Forwarded-For');

INSERT IGNORE INTO emulator_settings (`key`, `value`)
VALUES ('ws.enabled', 'true');
