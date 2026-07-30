CREATE TABLE IF NOT EXISTS `snowwar_arenas` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL,
    `model_name` VARCHAR(100) NULL,
    `is_official` TINYINT(1) NOT NULL DEFAULT 0,
    `is_active` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` INT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_snowwar_arenas_model` (`model_name`),
    KEY `idx_snowwar_arenas_pool` (`is_active`, `is_official`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=1000;

INSERT INTO `snowwar_arenas` (`id`, `name`, `model_name`, `is_official`, `is_active`) VALUES
    (8, 'Arctic Island', 'snowstorm_arena_8', 1, 1),
    (9, 'Dragon Cave', 'snowstorm_arena_9', 1, 1),
    (11, 'Fight Night', 'snowstorm_arena_11', 1, 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `model_name` = VALUES(`model_name`),
    `is_official` = 1,
    `is_active` = 1;

INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`) VALUES
    ('acc_snowwar_arena_build', 1, 'Allows staff to build and publish custom SnowWar arenas.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);
