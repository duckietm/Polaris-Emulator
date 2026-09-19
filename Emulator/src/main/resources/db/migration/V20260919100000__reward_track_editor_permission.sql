-- Reward track staff editor.
--
-- acc_rewardtrack lets a user open the editor inside the Reward Track window
-- (the gear tab) and create, change or delete tracks, tasks and prizes. Every
-- write reloads the tracks for everyone online. Every statement is idempotent.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_rewardtrack', 1, 'Allows editing the reward tracks (tasks, levels, prizes) from the Reward Track window.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every staff rank (5 and above), the same cut as :reloadrewards.
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
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''acc_rewardtrack'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
