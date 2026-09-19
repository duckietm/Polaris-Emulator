-- :reloadrewards command.
--
-- Reloads the reward tracks (tasks, levels, prizes) from the database and
-- pushes the fresh list to every online client, so staff edits show up without
-- a restart. :update_all runs the same reload. Every statement is idempotent.

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_update_reward_tracks', 'reloadrewards;updaterewards;update_reward_tracks'),
    ('commands.description.cmd_update_reward_tracks', ':reloadrewards'),
    ('commands.succes.cmd_update_reward_tracks', 'Reward tracks reloaded (%count% active).')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Permission definition for the command.
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_update_reward_tracks', 1, 'Allows using :reloadrewards to reload the reward tracks and push them to online users.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every staff rank (5 and above), the same cut as :maintenance.
-- Rank ids differ per hotel and the rank_<id> columns are created by the
-- permissions normalisation, so only the columns that actually exist are touched.
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
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_update_reward_tracks'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
