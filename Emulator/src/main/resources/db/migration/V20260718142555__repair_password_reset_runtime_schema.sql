-- Existing Polaris databases may have a CMS-era password_resets table. The
-- runtime writes by user_id and relies on unique user and token keys.

ALTER TABLE `password_resets`
    ADD COLUMN IF NOT EXISTS `user_id` INT(11) NULL FIRST,
    ADD COLUMN IF NOT EXISTS `created_ip` VARCHAR(64) NOT NULL DEFAULT '';

SET @password_resets_has_unique_user := (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'password_resets'
          AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1
           AND MAX(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'user_id' THEN 1 ELSE 0 END) = 1
    ) AS unique_user_indexes
);
SET @password_resets_user_ddl := IF(
    @password_resets_has_unique_user = 0,
    'ALTER TABLE `password_resets` ADD UNIQUE INDEX `idx_password_resets_user_id` (`user_id`)',
    'DO 0'
);
PREPARE password_resets_user_statement FROM @password_resets_user_ddl;
EXECUTE password_resets_user_statement;
DEALLOCATE PREPARE password_resets_user_statement;

SET @password_resets_has_unique_token := (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'password_resets'
          AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1
           AND MAX(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'token' THEN 1 ELSE 0 END) = 1
    ) AS unique_token_indexes
);
SET @password_resets_token_ddl := IF(
    @password_resets_has_unique_token = 0,
    'ALTER TABLE `password_resets` ADD UNIQUE INDEX `idx_password_resets_token` (`token`)',
    'DO 0'
);
PREPARE password_resets_token_statement FROM @password_resets_token_ddl;
EXECUTE password_resets_token_statement;
DEALLOCATE PREPARE password_resets_token_statement;
