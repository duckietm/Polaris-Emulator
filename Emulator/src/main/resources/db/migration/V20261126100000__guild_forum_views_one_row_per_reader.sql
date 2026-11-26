-- guild_forum_views holds one read marker per reader and forum: the time the reader last saw it. The base schema
-- has UNIQUE KEY user_guild (user_id, guild_id), but hotels that came from an older emulator have the table without
-- it. There every forum visit added another row, the marker read back as an arbitrary (often the oldest) row, and
-- a forum marked read showed as unread again after the next login.
--
-- Keep the newest row of each reader and forum (on a tie the highest id), then add the unique key when no unique
-- index on exactly (user_id, guild_id) exists yet. Both steps are no-ops on a table that already has one row per
-- pair and the key.

DELETE `older`
FROM `guild_forum_views` AS `older`
JOIN `guild_forum_views` AS `newer`
    ON `newer`.`user_id` = `older`.`user_id`
    AND `newer`.`guild_id` = `older`.`guild_id`
    AND (`newer`.`timestamp` > `older`.`timestamp`
        OR (`newer`.`timestamp` = `older`.`timestamp` AND `newer`.`id` > `older`.`id`));

SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'guild_forum_views'
          AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
    ) AS unique_indexes
    WHERE indexed_columns = 'user_id,guild_id'
);
SET @polaris_sql = IF(@polaris_index_exists = 0,
    'ALTER TABLE `guild_forum_views` ADD UNIQUE INDEX `user_guild` (`user_id`, `guild_id`)',
    'SELECT 1');
PREPARE polaris_statement FROM @polaris_sql;
EXECUTE polaris_statement;
DEALLOCATE PREPARE polaris_statement;
