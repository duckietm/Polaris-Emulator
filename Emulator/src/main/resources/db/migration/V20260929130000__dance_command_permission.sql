-- :dance checks cmd_dance, but the seed named the key cms_dance (set to 0 everywhere), so nobody
-- could use it. :dance only makes the user's own avatar dance, as the client already can, so
-- every rank gets it. Idempotent.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_dance', 1, 'Allows :dance <0-4> (dance with your own avatar).')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_dance'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
