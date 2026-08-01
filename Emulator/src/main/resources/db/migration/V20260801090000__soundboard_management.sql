ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `volume_soundboard` INT NOT NULL DEFAULT 80;

SET @soundboard_legacy_permission_existed := (
    SELECT COUNT(*)
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'permissions'
      AND `column_name` = 'acc_soundboard_manage'
);

SET @soundboard_permission_definition_existed := (
    SELECT COUNT(*)
    FROM `permission_definitions`
    WHERE `permission_key` = 'acc_soundboard_manage'
);

ALTER TABLE `permissions`
    ADD COLUMN IF NOT EXISTS `acc_soundboard_manage` ENUM('0','1') NOT NULL DEFAULT '0';

INSERT IGNORE INTO `permission_definitions` (`permission_key`, `max_value`, `comment`)
VALUES (
    'acc_soundboard_manage',
    1,
    'Allows global Soundboard catalog management in Housekeeping.'
);

SET @soundboard_rank_assignments := NULL;
SELECT GROUP_CONCAT(CONCAT('dst.`', `column_name`, '` = src.`', `column_name`, '`') SEPARATOR ', ')
    INTO @soundboard_rank_assignments
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'permission_definitions'
  AND `column_name` REGEXP '^rank_[0-9]+$';

SET @soundboard_rank_assignments_from_legacy := NULL;
SELECT GROUP_CONCAT(
        CONCAT(
            'dst.`', `column_name`, '` = COALESCE((SELECT MAX(CASE WHEN legacy_permissions.`acc_soundboard_manage` = ''1'' THEN 1 ELSE 0 END) ',
            'FROM `permissions` legacy_permissions WHERE legacy_permissions.`id` = ', SUBSTRING(`column_name`, 6), '), 0)'
        )
        SEPARATOR ', '
    )
    INTO @soundboard_rank_assignments_from_legacy
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'permission_definitions'
  AND `column_name` REGEXP '^rank_[0-9]+$';

SET @soundboard_permission_sql := IF(
    @soundboard_permission_definition_existed > 0,
    'UPDATE `permission_definitions` SET `max_value` = `max_value` WHERE 1 = 0',
    IF(
        @soundboard_legacy_permission_existed > 0,
        CONCAT(
            'UPDATE `permission_definitions` dst SET ', @soundboard_rank_assignments_from_legacy, ' ',
            'WHERE dst.`permission_key` = ''acc_soundboard_manage'''
        ),
        CONCAT(
            'UPDATE `permission_definitions` dst ',
            'JOIN `permission_definitions` src ON src.`permission_key` = ''acc_housekeeping'' ',
            'SET ', @soundboard_rank_assignments, ' ',
            'WHERE dst.`permission_key` = ''acc_soundboard_manage'''
        )
    )
);
PREPARE soundboard_permission_stmt FROM @soundboard_permission_sql;
EXECUTE soundboard_permission_stmt;
DEALLOCATE PREPARE soundboard_permission_stmt;

SET @soundboard_legacy_cases := NULL;
SELECT GROUP_CONCAT(
        CONCAT(
            'WHEN ', SUBSTRING(`column_name`, 6),
            ' THEN IF(src.`', `column_name`, '` > 0, ''1'', ''0'')'
        )
        ORDER BY CAST(SUBSTRING(`column_name`, 6) AS UNSIGNED)
        SEPARATOR ' '
    )
    INTO @soundboard_legacy_cases
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'permission_definitions'
  AND `column_name` REGEXP '^rank_[0-9]+$';

SET @soundboard_legacy_sql := IF(
    @soundboard_legacy_permission_existed > 0,
    'UPDATE `permissions` SET `id` = `id` WHERE 1 = 0',
    CONCAT(
        'UPDATE `permissions` legacy_permissions ',
        'JOIN `permission_definitions` src ON src.`permission_key` = ''acc_soundboard_manage'' ',
        'SET legacy_permissions.`acc_soundboard_manage` = CASE legacy_permissions.`id` ',
        @soundboard_legacy_cases,
        ' ELSE ''0'' END'
    )
);
PREPARE soundboard_legacy_stmt FROM @soundboard_legacy_sql;
EXECUTE soundboard_legacy_stmt;
DEALLOCATE PREPARE soundboard_legacy_stmt;

CREATE TABLE IF NOT EXISTS `logs_soundboard` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `staff_user_id` INT NOT NULL,
    `action` VARCHAR(16) NOT NULL,
    `sound_id` INT NULL,
    `before_snapshot` MEDIUMTEXT NULL,
    `after_snapshot` MEDIUMTEXT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_logs_soundboard_staff_created` (`staff_user_id`, `created_at`),
    KEY `idx_logs_soundboard_sound_created` (`sound_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
