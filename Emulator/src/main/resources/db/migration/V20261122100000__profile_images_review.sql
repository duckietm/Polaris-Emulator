-- Every picture uploaded to a profile, so staff can review the ones still shown and see who removed the others.
-- Every statement is idempotent.
CREATE TABLE IF NOT EXISTS `users_profile_images` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `user_id` INT(11) NOT NULL,
  `kind` ENUM('banner','background') NOT NULL,
  `file_name` VARCHAR(64) NOT NULL,
  `created_at` INT(11) NOT NULL,
  `removed_at` INT(11) NULL DEFAULT NULL,
  `removed_by` INT(11) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `users_profile_images_active` (`removed_at`, `created_at`),
  KEY `users_profile_images_user` (`user_id`),
  KEY `users_profile_images_file` (`file_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pictures uploaded before this table existed are still on profiles: list them for review too.
INSERT INTO `users_profile_images` (`user_id`, `kind`, `file_name`, `created_at`)
SELECT u.`id`, 'banner', u.`profile_banner_image`, UNIX_TIMESTAMP()
FROM `users` u
WHERE u.`profile_banner_image` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `users_profile_images` p WHERE p.`file_name` = u.`profile_banner_image`);

INSERT INTO `users_profile_images` (`user_id`, `kind`, `file_name`, `created_at`)
SELECT u.`id`, 'background', u.`profile_background_image`, UNIX_TIMESTAMP()
FROM `users` u
WHERE u.`profile_background_image` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `users_profile_images` p WHERE p.`file_name` = u.`profile_background_image`);

-- The housekeeping area that reviews them: every rank gets it with the value it has for acc_housekeeping.
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_hk_profile_images', 1, 'Housekeeping: review the pictures players uploaded to their profile, remove them and block uploads.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @copy_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('area.`', c.`column_name`, '` = hk.`', c.`column_name`, '`') ORDER BY r.`id` SEPARATOR ', ')
  INTO @copy_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @copy_sql = IF(@copy_sql IS NULL OR NOT EXISTS (SELECT 1 FROM `permission_definitions` WHERE `permission_key` = 'acc_housekeeping'),
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` area JOIN `permission_definitions` hk',
           ' ON hk.`permission_key` = ''acc_housekeeping''',
           ' SET ', @copy_sql,
           ' WHERE area.`permission_key` = ''acc_hk_profile_images'''));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;
