-- Index for the legacy private chat lookups by conversation pair, newest first:
--   chatlogs_private WHERE user_from_id = ? AND user_to_id = ? ORDER BY id DESC LIMIT ?
-- (JdbcMessengerHistoryRepository: the history of a conversation not yet stored in messenger_messages, and the
-- lines it adopts on its first stored message, which runs inside the send transaction.) The table only had one
-- index per column, so each lookup could fall back to scanning it. The table is MyISAM, whose secondary indexes do
-- not carry the primary key, so `id` is part of the index.
-- An existing index with the same ordered left prefix is reused; nothing is dropped.

-- chatlogs_private.idx_chatlogs_private_pair (user_from_id,user_to_id,id)
SET @polaris_index_columns = 'user_from_id,user_to_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chatlogs_private'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `chatlogs_private` ADD INDEX `idx_chatlogs_private_pair` (`user_from_id`, `user_to_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;
