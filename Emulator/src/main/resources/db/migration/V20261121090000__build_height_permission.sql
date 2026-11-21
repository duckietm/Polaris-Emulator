-- The build height widget gets the rank permission and the range settings the BuildHeight plugin had, under the
-- plugin's own names, so a hotel that ran the plugin keeps its choices. A hotel that did not gets every rank allowed
-- and no range rows: the widget then keeps the range it always had, the floor up to the furniture ceiling.
-- Every statement is idempotent.

SET @buildheight_known = (SELECT COUNT(*) FROM `permission_definitions` WHERE `permission_key` = 'cmd_buildheight');

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_buildheight', 1, 'Allows the build height widget in rooms where the user has build rights. 0 denied, 1 allowed.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every rank, but only when the permission is new: ranks a hotel already chose stay as they are.
SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @grant_sql = IF(@grant_sql IS NULL OR @buildheight_known > 0,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_buildheight'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
