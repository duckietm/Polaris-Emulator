-- The catalogue administration tools (catalog admin, catalog studio, furni editor) check acc_catalogfurni, which the
-- base database defines with 0 for every rank: nobody could open them, the Administrator included.
-- Rank 7 (Administrator in the base database) gets it once, here. A versioned migration runs only once, so a hotel
-- that takes it away again keeps its choice. Nothing happens when the hotel has no rank 7 or no rank_7 column.

SET @grant_sql = NULL;
SELECT CONCAT('`', c.`column_name`, '` = 1')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`id` = 7;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql,
           ' WHERE `permission_key` = ''acc_catalogfurni'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
