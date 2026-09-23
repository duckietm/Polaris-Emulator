-- Indexes for the marketplace search and its price history.
--
-- The public offer list aggregates the active listings of the search window
-- (state and listing time, then the listing price bounds) and counts the sales
-- of the day (state and sale time); the price history of a furni looks it up by
-- sprite. None of these filters had an index, so every search scanned the
-- listings and every price chart scanned the furni definitions.
--
-- Idempotent and non-destructive, like the query index contract: an existing
-- index with the same ordered left prefix is reused, and nothing is dropped. The
-- single-column `status` index of marketplace_items stays; the startup index
-- audit reports it as a redundant candidate and never removes it.

-- marketplace_items.idx_marketplace_items_state_timestamp_price (state,timestamp,price)
SET @polaris_index_columns = 'state,timestamp,price';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'marketplace_items'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `marketplace_items` ADD INDEX `idx_marketplace_items_state_timestamp_price` (`state`, `timestamp`, `price`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- marketplace_items.idx_marketplace_items_state_sold_timestamp (state,sold_timestamp)
SET @polaris_index_columns = 'state,sold_timestamp';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'marketplace_items'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `marketplace_items` ADD INDEX `idx_marketplace_items_state_sold_timestamp` (`state`, `sold_timestamp`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- items_base.idx_items_base_sprite_id (sprite_id)
SET @polaris_index_columns = 'sprite_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'items_base'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `items_base` ADD INDEX `idx_items_base_sprite_id` (`sprite_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;
