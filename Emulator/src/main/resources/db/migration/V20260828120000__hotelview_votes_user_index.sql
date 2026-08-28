-- Index hotelview_landing_votes by user_id.
--
-- The table's primary key is (slot_id, user_id), so lookups that filter only by
-- user_id -- the per-user landing request path -- cannot use it as a prefix and
-- fall back to a full scan. A secondary index on user_id keeps those lookups
-- cheap as the vote table grows.
--
-- Idempotent by design: hotel owners sometimes run the same schema patch twice.
-- A bare CREATE INDEX errors the second time (Duplicate key name), so it is
-- guarded through information_schema. IF NOT EXISTS is not used because MySQL 8
-- rejects it on indexes (MariaDB-only); the information_schema + PREPARE pattern
-- below works on both engines.

SET @add_index := (
    SELECT COUNT(*) FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'hotelview_landing_votes'
      AND `INDEX_NAME` = 'idx_hotelview_landing_votes_user');
SET @sql := IF(@add_index = 0,
    'CREATE INDEX `idx_hotelview_landing_votes_user` ON `hotelview_landing_votes` (`user_id`)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
