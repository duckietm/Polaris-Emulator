-- Keys for features the client offered by rank level only. Idempotent.
--   acc_gift_hide_sender   send a gift without your name (the server now enforces it)
--   acc_navigator_staff    staff-only room models and categories in the room creator
-- Each goes to the ranks that saw the feature before, by rank level.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_gift_hide_sender', 1, 'Allows sending a gift without showing the sender.'),
    ('acc_navigator_staff', 1, 'Shows staff-only room models and categories when creating a room.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`level` >= 5;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql,
           ' WHERE `permission_key` IN (''acc_gift_hide_sender'')'));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;

SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`level` >= 4;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql,
           ' WHERE `permission_key` IN (''acc_navigator_staff'')'));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;

-- The once-a-minute check for overrides that ran out reads them by expiry.
CREATE INDEX IF NOT EXISTS `expires_at` ON `user_permission_overrides` (`expires_at`);
