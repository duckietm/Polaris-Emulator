-- :furni (the furni chooser) without room rights. Idempotent.
--   acc_furni_chooser   open :furni in rooms where you have no rights
-- AIR allows it from security level 2 up, so ranks with level >= 2 get it; change the rank_* columns to choose
-- other ranks. Room rights holders, owners and ambassadors can always open it.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_furni_chooser', 1, 'Allows opening :furni (the furni chooser) in rooms without rights.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`level` >= 2;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql,
           ' WHERE `permission_key` IN (''acc_furni_chooser'')'));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
