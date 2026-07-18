-- ============================================================================
-- 014_access_token_version.sql
--
-- Signed access JWTs are otherwise valid until their exp claim even after
-- logout or a password change. This per-user version is embedded in new tokens
-- and incremented whenever all existing access tokens must be revoked.
--
-- Idempotent and portable: the information_schema guard skips the ALTER when
-- the column already exists, and avoids `ADD COLUMN IF NOT EXISTS`, which is
-- not valid syntax on MySQL 8.x (only MariaDB).
-- ============================================================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'access_token_version'
);

SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `users` ADD COLUMN `access_token_version` BIGINT NOT NULL DEFAULT 0 AFTER `remember_token_expires_at`',
    'SELECT ''access_token_version already present, skipping'' AS info'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
