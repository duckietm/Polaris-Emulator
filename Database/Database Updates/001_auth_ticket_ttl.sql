-- ============================================================================
-- 020_auth_ticket_ttl.sql
--
-- Adds an explicit expiry timestamp to the SSO auth_ticket on `users`.
--
-- This legacy compatibility column may still be used by a CMS, but the emulator
-- no longer requires external issuers to populate it. Run
-- 013_auth_ticket_sessions.sql to enable emulator-managed hashed ticket TTLs.
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
