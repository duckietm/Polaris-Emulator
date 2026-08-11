-- Achievement badge integrity. The unlock path was not atomic under concurrent
-- progress (rapid pet scratching runs on pool threads) and the asynchronous badge
-- rename could execute before its own insert, so hotels accumulated duplicate
-- ACH_* badge rows and duplicate users_achievements progress rows. The emulator
-- now upgrades badges atomically and sweeps stale lineage rows; this migration
-- repairs existing data and then locks both invariants in at the schema level.

-- 1. Exact duplicate badge rows: keep the oldest row per (user_id, badge_code).
DELETE b
FROM `users_badges` b
JOIN `users_badges` keep_row
  ON keep_row.`user_id` = b.`user_id`
 AND keep_row.`badge_code` = b.`badge_code`
 AND keep_row.`id` < b.`id`;

-- 2. One badge row per user and code. Only an existing UNIQUE key on exactly these
--    columns satisfies the requirement; the non-unique query indexes added by
--    V20260718190000 share the prefix but do not enforce anything.
SET @polaris_unique_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_badges' AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
    ) existing_unique_keys
    WHERE indexed_columns = 'user_id,badge_code'
);
SET @polaris_unique_sql = IF(
    @polaris_unique_exists > 0,
    'DO 0',
    'ALTER TABLE `users_badges` ADD UNIQUE KEY `uq_users_badges_user_badge` (`user_id`, `badge_code`)'
);
PREPARE polaris_unique_statement FROM @polaris_unique_sql;
EXECUTE polaris_unique_statement;
DEALLOCATE PREPARE polaris_unique_statement;

-- 3. Duplicate progress rows. users_achievements has no primary key, so equal rows
--    cannot be told apart for a targeted delete; rebuild keeping the highest
--    progress per (user_id, achievement_name) and swap atomically. CREATE ... LIKE
--    preserves the hotel's existing engine, charset and keys.
DROP TABLE IF EXISTS `users_achievements_dedup`;

CREATE TABLE `users_achievements_dedup` LIKE `users_achievements`;

INSERT INTO `users_achievements_dedup` (`user_id`, `achievement_name`, `progress`)
SELECT `user_id`, `achievement_name`, MAX(`progress`)
FROM `users_achievements`
GROUP BY `user_id`, `achievement_name`;

RENAME TABLE `users_achievements` TO `users_achievements_replaced`,
             `users_achievements_dedup` TO `users_achievements`;

DROP TABLE `users_achievements_replaced`;

-- 4. One progress row per user and achievement.
SET @polaris_unique_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_achievements' AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
    ) existing_unique_keys
    WHERE indexed_columns = 'user_id,achievement_name'
);
SET @polaris_unique_sql = IF(
    @polaris_unique_exists > 0,
    'DO 0',
    'ALTER TABLE `users_achievements` ADD UNIQUE KEY `uq_users_achievements_user_achievement` (`user_id`, `achievement_name`)'
);
PREPARE polaris_unique_statement FROM @polaris_unique_sql;
EXECUTE polaris_unique_statement;
DEALLOCATE PREPARE polaris_unique_statement;
