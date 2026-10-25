-- users_navigator_settings holds a user's list type and collapsed state per navigator category. Its only
-- unique key was on user_id, so a user could keep one category: every later insert failed with a duplicate
-- key - an error in the log per packet - and the preferences of every other category were lost.
--
-- The key moves to (user_id, caption); the emulator now writes with INSERT ... ON DUPLICATE KEY UPDATE.
-- No row is removed: with the old key there is at most one row per user, so nothing collides. The table
-- is small; the statements below are rerun-safe.

-- 1. Drop the one-row-per-user key, but only in its known shape: unique, named userid, on user_id alone.
SET @old_key := (SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`)
                 FROM information_schema.STATISTICS
                 WHERE `TABLE_SCHEMA` = DATABASE()
                   AND `TABLE_NAME` = 'users_navigator_settings'
                   AND `INDEX_NAME` = 'userid'
                   AND `NON_UNIQUE` = 0);
SET @ddl := IF(@old_key = 'user_id',
    'ALTER TABLE `users_navigator_settings` DROP INDEX `userid`',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. A hand-edited table could hold one category twice for a user; the key cannot be added while it does.
-- Each such pair is rebuilt down to one row (the visible, list-mode values win). A no-op on a stock table.
CREATE TEMPORARY TABLE `navigator_settings_dedupe` AS
SELECT `user_id`,
       `caption`,
       MIN(`list_type`) AS `list_type`,
       MAX(`display`)   AS `display`
FROM `users_navigator_settings`
GROUP BY `user_id`, `caption`
HAVING COUNT(*) > 1;

DELETE `n`
FROM `users_navigator_settings` `n`
JOIN `navigator_settings_dedupe` `d`
  ON `d`.`user_id` = `n`.`user_id`
 AND `d`.`caption` = `n`.`caption`;

INSERT INTO `users_navigator_settings` (`user_id`, `caption`, `list_type`, `display`)
SELECT `user_id`, `caption`, `list_type`, `display` FROM `navigator_settings_dedupe`;

DROP TEMPORARY TABLE `navigator_settings_dedupe`;

-- 3. One row per user and category.
ALTER TABLE `users_navigator_settings`
    ADD UNIQUE KEY IF NOT EXISTS `uq_users_navigator_settings_caption` (`user_id`, `caption`);
