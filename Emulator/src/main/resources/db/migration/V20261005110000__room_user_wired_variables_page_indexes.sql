-- Indexes for the variables web API's holder pages of a user variable.
--
-- A page merges the users in the room with the saved rows of users who are not,
-- reading those rows with ORDER BY value, created_at or updated_at (the user id
-- breaking ties) and LIMIT/OFFSET for one room and variable. Without an index on
-- the sort key every page sorted all saved rows of the variable.
--
-- Idempotent and non-destructive, like the query index contract: an existing
-- index with the same ordered left prefix is reused, and nothing is dropped.

-- room_user_wired_variables.idx_room_user_wired_variables_room_item_value (room_id,variable_item_id,value,user_id)
SET @polaris_index_columns = 'room_id,variable_item_id,value,user_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_user_wired_variables'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_user_wired_variables` ADD INDEX `idx_room_user_wired_variables_room_item_value` (`room_id`, `variable_item_id`, `value`, `user_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_user_wired_variables.idx_room_user_wired_variables_room_item_created (room_id,variable_item_id,created_at,user_id)
SET @polaris_index_columns = 'room_id,variable_item_id,created_at,user_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_user_wired_variables'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_user_wired_variables` ADD INDEX `idx_room_user_wired_variables_room_item_created` (`room_id`, `variable_item_id`, `created_at`, `user_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_user_wired_variables.idx_room_user_wired_variables_room_item_updated (room_id,variable_item_id,updated_at,user_id)
SET @polaris_index_columns = 'room_id,variable_item_id,updated_at,user_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_user_wired_variables'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_user_wired_variables` ADD INDEX `idx_room_user_wired_variables_room_item_updated` (`room_id`, `variable_item_id`, `updated_at`, `user_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;
