-- Indexes for the queries that run on every login and every room load:
--   bots WHERE room_id = ?            (RoomLoadOperations, RoomUnitManager)
--   bots WHERE user_id = ? AND room_id = 0   (BotsComponent)
--   messenger_offline WHERE user_id = ? ORDER BY id   (Messenger offline messages)
-- Existing indexes with the same ordered left prefix are reused; nothing is dropped.

-- bots.idx_bots_room_id (room_id)
SET @polaris_index_columns = 'room_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bots'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `bots` ADD INDEX `idx_bots_room_id` (`room_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- bots.idx_bots_user_room (user_id,room_id)
SET @polaris_index_columns = 'user_id,room_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bots'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `bots` ADD INDEX `idx_bots_user_room` (`user_id`, `room_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- messenger_offline.idx_messenger_offline_user_id (user_id,id)
SET @polaris_index_columns = 'user_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messenger_offline'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `messenger_offline` ADD INDEX `idx_messenger_offline_user_id` (`user_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;
