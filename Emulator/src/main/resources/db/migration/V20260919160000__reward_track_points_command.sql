-- :rewardpoints command.
--
-- :rewardpoints <user> <points> [track] gives reward track points to an
-- online user, or takes them away with a negative number; the total never
-- goes below zero. Without a track it acts on the first active one. Every
-- statement is idempotent.

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_reward_points', 'rewardpoints;rtpoints'),
    ('commands.description.cmd_reward_points', ':rewardpoints <user> <points> [track]'),
    ('commands.succes.cmd_reward_points', '%user% now has %points% points on %track% (%delta%).'),
    ('commands.error.cmd_reward_points', 'Usage: :rewardpoints <user> <points> [track]'),
    ('commands.error.cmd_reward_points.offline', '%user% is not online.'),
    ('commands.error.cmd_reward_points.track', 'No such reward track.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Permission definition for the command.
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_reward_points', 1, 'Allows using :rewardpoints to give or take reward track points from an online user.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every staff rank (5 and above), the same cut as :reloadrewards.
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
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_reward_points'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
