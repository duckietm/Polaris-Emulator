-- ============================================================
-- Nick Icon Customization Setup
-- ============================================================

CREATE TABLE IF NOT EXISTS `custom_nick_icons_catalog` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `icon_key` VARCHAR(50) NOT NULL,
    `display_name` VARCHAR(100) NOT NULL DEFAULT '',
    `points` INT(11) NOT NULL DEFAULT 0,
    `points_type` INT(11) NOT NULL DEFAULT 0,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_icon_key` (`icon_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_nick_icons` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `user_id` INT(11) NOT NULL,
    `icon_key` VARCHAR(50) NOT NULL,
    `active` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_icon` (`user_id`, `icon_key`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_active` (`user_id`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `custom_nick_icons_catalog` (`icon_key`, `display_name`, `points`, `points_type`, `enabled`, `sort_order`) VALUES
    ('1', 'Icon 1', 10, 0, 1, 1),
    ('2', 'Icon 2', 10, 0, 1, 2),
    ('3', 'Icon 3', 10, 0, 1, 3),
    ('4', 'Icon 4', 10, 0, 1, 4),
    ('5', 'Icon 5', 10, 0, 1, 5),
    ('6', 'Icon 6', 10, 0, 1, 6);
ALTER TABLE `custom_nick_icons_catalog`
    ADD COLUMN IF NOT EXISTS `display_name` VARCHAR(100) NOT NULL DEFAULT '' AFTER `icon_key`;
	
ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `remember_token_hash` VARCHAR(64) NOT NULL DEFAULT '' AFTER `auth_ticket`;

ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `remember_token_expires_at` INT(11) UNSIGNED NOT NULL DEFAULT 0 AFTER `remember_token_hash`;

ALTER TABLE `users`
    ADD INDEX IF NOT EXISTS `idx_users_remember_token_hash` (`remember_token_hash`);


INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`)
VALUES ('hotel.wired.message.max_length', '512', 'Maximum length of text fields used by wired messages and bot text effects.')
ON DUPLICATE KEY UPDATE `value` = '512';

