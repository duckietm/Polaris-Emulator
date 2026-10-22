-- Epic popup (AIR EpicPopupMessageEvent / HabboEpicPopupView): :epicpopup <image> [user] and the RCON/CMS
-- command epicpopup show one framed image to the hotel or one user. Every statement is idempotent.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.epic_popup.folder', 'notifications/', 'Epic popup: folder of the image library that a plain image name of :epicpopup is looked up in.')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_epic_popup', 'epicpopup;epic'),
    ('commands.description.cmd_epic_popup', ':epicpopup <image> [user]'),
    ('commands.succes.cmd_epic_popup', 'Epic popup shown to %count% users.'),
    ('commands.error.cmd_epic_popup', 'Usage: :epicpopup <image> [user]'),
    ('commands.error.cmd_epic_popup.image', 'That is not a usable image name or URL.'),
    ('commands.error.cmd_epic_popup.offline', '%user% is not online.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Permission definition for the command.
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_epic_popup', 1, 'Allows using :epicpopup to show an epic popup image to the hotel or one user.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every staff rank (5 and above).
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
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_epic_popup'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
