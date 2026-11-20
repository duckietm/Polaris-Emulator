-- Pictures a player uploads for the banner and the background of the profile window, the flag that keeps a player
-- from uploading, and :profileimage for staff to remove them. Every statement is idempotent.
ALTER TABLE users
ADD COLUMN IF NOT EXISTS `profile_banner_image` VARCHAR(64) NULL DEFAULT NULL AFTER `profile_accent_id`,
ADD COLUMN IF NOT EXISTS `profile_background_image` VARCHAR(64) NULL DEFAULT NULL AFTER `profile_banner_image`,
ADD COLUMN IF NOT EXISTS `profile_images_blocked` TINYINT(1) NOT NULL DEFAULT 0 AFTER `profile_background_image`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_profileimage', ':profileimage <username> <remove|block|unblock>'),
    ('commands.succes.cmd_profileimage.remove', 'The profile pictures of %username% were removed.'),
    ('commands.succes.cmd_profileimage.block', 'The profile pictures of %username% were removed and they can no longer upload.'),
    ('commands.succes.cmd_profileimage.unblock', '%username% can upload profile pictures again.'),
    ('commands.error.cmd_profileimage.usage', 'Use :profileimage <username> <remove|block|unblock>'),
    ('commands.error.cmd_profileimage.not_found', 'There is no user called %username%.')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_profileimage', 1, 'Allows using :profileimage to remove the pictures a user uploaded to their profile and to stop them uploading.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- The ranks that may ban may also remove profile pictures.
SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('p.`', c.`column_name`, '` = s.`', c.`column_name`, '`') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @grant_sql = IF(@grant_sql IS NULL OR NOT EXISTS (SELECT 1 FROM `permission_definitions` WHERE `permission_key` = 'cmd_ban'),
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` p JOIN `permission_definitions` s ON s.`permission_key` = ''cmd_ban'' SET ',
        @grant_sql, ' WHERE p.`permission_key` = ''cmd_profileimage'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
