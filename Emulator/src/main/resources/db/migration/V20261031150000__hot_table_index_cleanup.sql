-- Indexes on the busiest tables that cost every write and serve no query, and two lookups that had none.
--
-- Dropped (each only if it exists, so a hotel that never had it, or already removed it, is a no-op):
--   users.id, users.id_2, users.id_3   three UNIQUE copies of the primary key
--   users.figure                       (motto, look, gender): no query filters on those columns
--   items.extra_data, items.wired_data  rewritten on almost every furni state change; nothing searches them
--   items.id                           a copy of the primary key
--   chatlogs_room.message, chatlogs_private.message
--                                      every chat line pays for them; the moderation search uses LIKE '%...%',
--                                      which cannot use them
--   chatlogs_private.id                a copy of the primary key
--
-- Lock and runtime: on InnoDB (users, items) dropping a secondary index changes metadata only. The chat log tables
-- are MyISAM, where dropping an index rebuilds the table once; on a large log that runs at startup, after the
-- automatic pre-migration backup, while the hotel is not yet accepting connections. Recovery is the backup or
-- re-adding the index. Converting the chat logs to InnoDB is not part of this migration.
--
-- Added, reusing an existing index with the same left prefix:
--   users(ip_register)  the per-IP registration quota: SELECT COUNT(*) FROM users WHERE ip_register = ?
--   users(mail)         registration's duplicate check: WHERE username = ? OR mail = ?

ALTER TABLE `users` DROP INDEX IF EXISTS `id`;
ALTER TABLE `users` DROP INDEX IF EXISTS `id_2`;
ALTER TABLE `users` DROP INDEX IF EXISTS `id_3`;
ALTER TABLE `users` DROP INDEX IF EXISTS `figure`;

ALTER TABLE `items` DROP INDEX IF EXISTS `extra_data`;
ALTER TABLE `items` DROP INDEX IF EXISTS `wired_data`;
ALTER TABLE `items` DROP INDEX IF EXISTS `id`;

ALTER TABLE `chatlogs_room` DROP INDEX IF EXISTS `message`;
ALTER TABLE `chatlogs_private` DROP INDEX IF EXISTS `message`, DROP INDEX IF EXISTS `id`;

-- users.idx_users_ip_register (ip_register)
SET @polaris_index_columns = 'ip_register';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users` ADD INDEX `idx_users_ip_register` (`ip_register`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- users.idx_users_mail (mail)
SET @polaris_index_columns = 'mail';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users` ADD INDEX `idx_users_mail` (`mail`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;
