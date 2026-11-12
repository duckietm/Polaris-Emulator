-- :ping gets its texts and a permission of its own. Until now the command was open to everyone with fixed English
-- texts; every rank is granted the permission, so who may use it does not change. Every statement is idempotent.

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_ping', 'ping'),
    ('commands.description.cmd_ping', ':ping'),
    ('commands.succes.cmd_ping', 'Average ping %average%ms, last ping %last%ms'),
    ('commands.error.cmd_ping.not_ready', 'Your ping has not been measured yet, please try again in a minute.')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_ping', 1, 'Allows using :ping to see the round trip the server measured for your own connection.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every rank.
SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_ping'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
