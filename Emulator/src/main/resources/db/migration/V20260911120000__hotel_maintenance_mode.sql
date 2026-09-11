-- Hotel maintenance mode (:maintenance command + login gate).
--
-- While hotel.maintenance.enabled = 1 only ranks >= hotel.maintenance.min_rank
-- may start a session: the HTTP auth API (login / remember / refresh /
-- sso-token) and the game-socket login both refuse lower ranks. The login
-- page shows hotel.maintenance.message. Every statement is idempotent.

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('hotel.maintenance.enabled', '0'),
    ('hotel.maintenance.message', 'The hotel is currently undergoing maintenance. Please try again later.'),
    ('hotel.maintenance.min_rank', '5')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_maintenance', 'maintenance;maintenancemode'),
    ('commands.description.cmd_maintenance', ':maintenance <on|off> [message]'),
    ('commands.succes.cmd_maintenance.on', 'Maintenance mode enabled. Only rank %rank%+ can log in now.'),
    ('commands.succes.cmd_maintenance.off', 'Maintenance mode disabled. Everyone can log in again.'),
    ('commands.error.cmd_maintenance', 'Usage: :maintenance <on|off> [message]'),
    ('commands.generic.cmd_maintenance.status', 'Maintenance mode is %state% (min rank %rank%): %message%'),
    ('commands.generic.cmd_maintenance.on', 'ON'),
    ('commands.generic.cmd_maintenance.off', 'OFF')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Permission definition for the command.
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_maintenance', 1, 'Allows using :maintenance to toggle hotel maintenance mode (login restricted to hotel.maintenance.min_rank and above).')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every rank at or above the default maintenance rank (5) so staff
-- can toggle it without a manual grant. Rank ids differ per hotel and the
-- rank_<id> columns are created by the permissions normalisation, so only the
-- columns that actually exist are touched.
SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`id` >= 5;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_maintenance'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
