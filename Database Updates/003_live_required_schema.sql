-- ============================================================
-- Live required schema
-- ============================================================
-- Consolidated schema for the currently used Nitro/Arcturus live
-- additions. This file intentionally excludes old/unused migration
-- artifacts and dump-only data.
--
-- Scope:
-- - tables/columns currently referenced by Java code
-- - runtime settings required by secure assets/API, login, wired, and UI
-- - safe CREATE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS statements
--
-- Assumes the base Arcturus database already exists.
-- Tested for MariaDB-style syntax used by this project.
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- Core settings support
-- ------------------------------------------------------------

ALTER TABLE `emulator_settings`
    ADD COLUMN IF NOT EXISTS `comment` TEXT NULL DEFAULT '' AFTER `value`;

ALTER TABLE catalog_pages
	ADD COLUMN IF NOT EXISTS `catalog_mode` ENUM('NORMAL', 'BUILDER', 'BOTH') NOT NULL DEFAULT 'NORMAL' AFTER `includes`;


CREATE TABLE IF NOT EXISTS `wired_emulator_settings` (
    `key` VARCHAR(255) NOT NULL,
    `value` TEXT NOT NULL,
    `comment` TEXT NULL DEFAULT '',
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('crypto.ws.enabled', '0'),
    ('crypto.ws.signing.enabled', '0'),
    ('crypto.ws.signing.public_key', ''),
    ('crypto.ws.signing.private_key', ''),
    ('login.access.jwt.secret', ''),
    ('login.remember.duration.days', '30'),
    ('login.remember.rotate.interval.minutes', '15'),
    ('login.remember.jwt.secret', ''),
    ('login.turnstile.enabled', '0'),
    ('login.turnstile.sitekey', ''),
    ('login.turnstile.secretkey', ''),
    ('login.ratelimit.enabled', '1'),
    ('login.ratelimit.max_attempts', '5'),
    ('login.ratelimit.window_sec', '60'),
    ('login.ratelimit.lockout_sec', '120'),
    ('login.register.enabled', '1'),
    ('register.max_per_ip', '5'),
    ('register.default.look', 'hr-100-7.hd-180-1.ch-210-66.lg-270-82.sh-290-80'),
    ('register.default.motto', 'I love Habbo!'),
    ('password.reset.url', 'http://localhost/reset-password'),
    ('smtp.provider', 'own'),
    ('smtp.host', 'localhost'),
    ('smtp.port', '587'),
    ('smtp.username', ''),
    ('smtp.password', ''),
    ('smtp.from_address', 'no-reply@example.com'),
    ('smtp.from_name', 'Habbo Hotel'),
    ('smtp.use_tls', '1'),
    ('smtp.use_ssl', '0'),
    ('new_user_credits', '0'),
    ('new_user_duckets', '0'),
    ('new_user_diamonds', '0')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wired.engine.enabled', '1', 'Compatibility flag. The runtime uses the new wired engine.'),
    ('wired.engine.exclusive', '1', 'Compatibility flag. The runtime uses exclusive wired engine execution.'),
    ('wired.engine.maxStepsPerStack', '100', 'Maximum internal processing steps allowed for a single wired stack execution.'),
    ('wired.engine.debug', '0', 'Enable verbose debug logging for the wired engine.'),
    ('wired.custom.enabled', '0', 'Enable custom legacy wired compatibility behavior.'),
    ('hotel.wired.furni.selection.count', '5', 'Maximum number of furni that a wired box can store or select.'),
    ('hotel.wired.max_delay', '20', 'Maximum delay value accepted by wired effects that support delayed execution.'),
    ('hotel.wired.message.max_length', '512', 'Maximum length of wired message text fields.'),
    ('wired.effect.teleport.delay', '500', 'Delay in milliseconds used by wired teleport movement.'),
    ('wired.place.under', '0', 'Allow placing wired furniture underneath other items when room rules permit it.'),
    ('wired.tick.interval.ms', '50', 'Global wired tick interval in milliseconds.'),
    ('wired.tick.resolution', '100', 'Legacy wired tick resolution value.'),
    ('wired.tick.debug', '0', 'Enable verbose logging for the wired tick service.'),
    ('wired.tick.thread.priority', '6', 'Java thread priority used by the wired tick service.'),
    ('wired.highscores.displaycount', '25', 'Maximum number of wired highscore entries shown to users.'),
    ('wired.abuse.max.recursion.depth', '10', 'Maximum recursive wired depth before execution is stopped.'),
    ('wired.abuse.max.events.per.window', '100', 'Maximum identical wired events allowed inside the abuse rate-limit window.'),
    ('wired.abuse.rate.limit.window.ms', '10000', 'Wired abuse rate-limit window in milliseconds.'),
    ('wired.abuse.ban.duration.ms', '600000', 'Temporary wired ban duration after abuse detection.'),
    ('wired.monitor.usage.window.ms', '1000', 'Rolling window size for wired usage monitoring.'),
    ('wired.monitor.usage.limit', '1000', 'Maximum wired usage budget in one monitor window.'),
    ('wired.monitor.delayed.events.limit', '100', 'Maximum delayed wired events queued in one room.'),
    ('wired.monitor.overload.average.ms', '50', 'Average execution time threshold for overload tracking.'),
    ('wired.monitor.overload.peak.ms', '150', 'Peak execution time threshold for overload tracking.'),
    ('wired.monitor.overload.consecutive.windows', '2', 'Consecutive overloaded windows required before logging overload.'),
    ('wired.monitor.heavy.usage.percent', '70', 'Usage percentage threshold for heavy-room tracking.'),
    ('wired.monitor.heavy.consecutive.windows', '5', 'Consecutive windows above heavy usage threshold.'),
    ('wired.monitor.heavy.delayed.percent', '60', 'Delayed queue percentage threshold for heavy-room tracking.')
ON DUPLICATE KEY UPDATE
    `value` = VALUES(`value`),
    `comment` = VALUES(`comment`);

-- ------------------------------------------------------------
-- Login API, room templates, remember-me, and news
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `password_resets` (
    `user_id` INT(11) NOT NULL,
    `token` VARCHAR(128) NOT NULL,
    `expires_at` TIMESTAMP NOT NULL,
    `created_ip` VARCHAR(64) NOT NULL DEFAULT '',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `idx_password_resets_token` (`token`),
    CONSTRAINT `fk_password_resets_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_remember_families` (
    `family_id` CHAR(36) NOT NULL,
    `user_id` INT(11) NOT NULL,
    `current_version` INT(11) NOT NULL DEFAULT 1,
    `created_at` INT(11) NOT NULL,
    `expires_at` INT(11) NOT NULL,
    `revoked` TINYINT(1) NOT NULL DEFAULT 0,
    `last_ip` VARCHAR(45) NOT NULL DEFAULT '',
    PRIMARY KEY (`family_id`),
    KEY `idx_users_remember_families_user_id` (`user_id`),
    KEY `idx_users_remember_families_expires_at` (`expires_at`),
    CONSTRAINT `fk_users_remember_families_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `room_templates` (
    `template_id` INT(11) NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(128) NOT NULL DEFAULT '',
    `description` VARCHAR(256) NOT NULL DEFAULT '',
    `thumbnail` VARCHAR(512) NOT NULL DEFAULT '',
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    `enabled` ENUM('0','1') NOT NULL DEFAULT '1',
    `name` VARCHAR(50) NOT NULL DEFAULT '',
    `room_description` VARCHAR(250) NOT NULL DEFAULT '',
    `model` VARCHAR(100) NOT NULL,
    `password` VARCHAR(50) NOT NULL DEFAULT '',
    `state` ENUM('open','locked','password','invisible') NOT NULL DEFAULT 'open',
    `users_max` INT(11) NOT NULL DEFAULT 25,
    `category` INT(11) NOT NULL DEFAULT 0,
    `paper_floor` VARCHAR(50) NOT NULL DEFAULT '0.0',
    `paper_wall` VARCHAR(50) NOT NULL DEFAULT '0.0',
    `paper_landscape` VARCHAR(50) NOT NULL DEFAULT '0.0',
    `thickness_wall` INT(11) NOT NULL DEFAULT 0,
    `thickness_floor` INT(11) NOT NULL DEFAULT 0,
    `moodlight_data` VARCHAR(2048) NOT NULL DEFAULT '',
    `override_model` ENUM('0','1') NOT NULL DEFAULT '0',
    `trade_mode` INT(2) NOT NULL DEFAULT 2,
    `heightmap` MEDIUMTEXT NOT NULL,
    `door_x` INT(11) NOT NULL DEFAULT 0,
    `door_y` INT(11) NOT NULL DEFAULT 0,
    `door_dir` INT(4) NOT NULL DEFAULT 2,
    PRIMARY KEY (`template_id`),
    KEY `idx_room_templates_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `room_templates_items` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `template_id` INT(11) NOT NULL,
    `item_id` INT(11) UNSIGNED NOT NULL,
    `wall_pos` VARCHAR(20) NOT NULL DEFAULT '',
    `x` INT(11) NOT NULL DEFAULT 0,
    `y` INT(11) NOT NULL DEFAULT 0,
    `z` DOUBLE(10,6) NOT NULL DEFAULT 0.000000,
    `rot` INT(11) NOT NULL DEFAULT 0,
    `extra_data` VARCHAR(2096) NOT NULL DEFAULT '',
    `wired_data` VARCHAR(4096) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_room_templates_items_template_id` (`template_id`),
    KEY `idx_room_templates_items_item_id` (`item_id`),
    CONSTRAINT `fk_room_templates_items_template`
        FOREIGN KEY (`template_id`) REFERENCES `room_templates` (`template_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_room_templates_items_item_base`
        FOREIGN KEY (`item_id`) REFERENCES `items_base` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `ui_news` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(150) NOT NULL,
    `body` TEXT NOT NULL,
    `image` MEDIUMTEXT DEFAULT NULL,
    `link_text` VARCHAR(80) NOT NULL DEFAULT '',
    `link_url` VARCHAR(255) NOT NULL DEFAULT '',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ui_news_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

INSERT INTO `ui_news` (`title`, `body`, `image`, `link_text`, `link_url`, `enabled`, `sort_order`)
SELECT 'Welcome to the Hotel!', 'Catch up on the latest events, updates and competitions happening right now in the hotel.', '', '', '', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `ui_news`);

-- ------------------------------------------------------------
-- Wired runtime data
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `room_wired_settings` (
    `room_id` INT(11) NOT NULL,
    `inspect_mask` INT(11) NOT NULL DEFAULT 0 COMMENT 'Bitmask for who can open and inspect Wired in the room. 1=everyone, 2=users with rights, 4=group members, 8=group admins.',
    `modify_mask` INT(11) NOT NULL DEFAULT 0 COMMENT 'Bitmask for who can modify Wired in the room. 2=users with rights, 4=group members, 8=group admins.',
    PRIMARY KEY (`room_id`),
    CONSTRAINT `fk_room_wired_settings_room`
        FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_wired_variables` (
    `room_id` INT(11) NOT NULL,
    `variable_item_id` INT(11) NOT NULL,
    `value` INT(11) DEFAULT NULL,
    `created_at` INT(11) NOT NULL DEFAULT 0,
    `updated_at` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`, `variable_item_id`),
    KEY `idx_room_wired_variables_room_item` (`room_id`, `variable_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_user_wired_variables` (
    `room_id` INT(11) NOT NULL,
    `user_id` INT(11) NOT NULL,
    `variable_item_id` INT(11) NOT NULL,
    `value` INT(11) DEFAULT NULL,
    `created_at` INT(11) NOT NULL DEFAULT 0,
    `updated_at` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`, `user_id`, `variable_item_id`),
    KEY `idx_room_user_wired_variables_room_item` (`room_id`, `variable_item_id`),
    KEY `idx_room_user_wired_variables_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_furni_wired_variables` (
    `room_id` INT(11) NOT NULL,
    `furni_id` INT(11) NOT NULL,
    `variable_item_id` INT(11) NOT NULL,
    `value` INT(11) DEFAULT NULL,
    `created_at` INT(11) NOT NULL DEFAULT 0,
    `updated_at` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`, `furni_id`, `variable_item_id`),
    KEY `idx_room_furni_wired_variables_room_item` (`room_id`, `variable_item_id`),
    KEY `idx_room_furni_wired_variables_furni` (`furni_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- User customization: prefixes, nick icons, profile backgrounds
-- ------------------------------------------------------------

ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `background_id` INT(11) NOT NULL DEFAULT 0 AFTER `machine_id`,
    ADD COLUMN IF NOT EXISTS `background_stand_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_id`,
    ADD COLUMN IF NOT EXISTS `background_overlay_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_stand_id`,
    ADD COLUMN IF NOT EXISTS `background_card_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_overlay_id`;

CREATE TABLE IF NOT EXISTS `infostand_backgrounds` (
    `id` INT(11) NOT NULL,
    `category` ENUM('background','stand','overlay','card') NOT NULL,
    `min_rank` INT(11) NOT NULL DEFAULT 0,
    `is_hc_only` TINYINT(1) NOT NULL DEFAULT 0,
    `is_ambassador_only` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `infostand_backgrounds` (`id`, `category`, `min_rank`, `is_hc_only`, `is_ambassador_only`) VALUES
    (0, 'background', 0, 0, 0),
    (0, 'stand', 0, 0, 0),
    (0, 'overlay', 0, 0, 0),
    (0, 'card', 0, 0, 0);

CREATE TABLE IF NOT EXISTS `user_prefixes` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `user_id` INT(11) NOT NULL,
    `text` VARCHAR(50) NOT NULL,
    `color` VARCHAR(255) NOT NULL DEFAULT '#FFFFFF',
    `icon` VARCHAR(50) NOT NULL DEFAULT '',
    `effect` VARCHAR(50) NOT NULL DEFAULT '',
    `font` VARCHAR(50) NOT NULL DEFAULT '',
    `catalog_prefix_id` INT(11) NOT NULL DEFAULT 0,
    `display_name` VARCHAR(100) NOT NULL DEFAULT '',
    `points` INT(11) NOT NULL DEFAULT 0,
    `points_type` INT(11) NOT NULL DEFAULT 0,
    `is_custom` TINYINT(1) NOT NULL DEFAULT 1,
    `active` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_prefixes_user_id` (`user_id`),
    KEY `idx_user_prefixes_user_active` (`user_id`, `active`),
    CONSTRAINT `fk_user_prefixes_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `custom_prefixes_catalog` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `display_name` VARCHAR(100) NOT NULL DEFAULT '',
    `text` VARCHAR(50) NOT NULL,
    `color` VARCHAR(255) NOT NULL DEFAULT '#FFFFFF',
    `icon` VARCHAR(50) NOT NULL DEFAULT '',
    `effect` VARCHAR(50) NOT NULL DEFAULT '',
    `font` VARCHAR(50) NOT NULL DEFAULT '',
    `points` INT(11) NOT NULL DEFAULT 0,
    `points_type` INT(11) NOT NULL DEFAULT 0,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_visual_settings` (
    `user_id` INT(11) NOT NULL,
    `display_order` VARCHAR(50) NOT NULL DEFAULT 'icon-prefix-name',
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_user_visual_settings_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `custom_prefix_settings` (
    `key_name` VARCHAR(100) NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `custom_prefix_settings` (`key_name`, `value`) VALUES
    ('max_length', '15'),
    ('min_rank_to_buy', '1'),
    ('price_credits', '5'),
    ('price_points', '0'),
    ('points_type', '0'),
    ('font_price_credits', '10'),
    ('font_price_points', '0'),
    ('font_points_type', '0');

INSERT IGNORE INTO `custom_prefixes_catalog`
    (`id`, `display_name`, `text`, `color`, `icon`, `effect`, `font`, `points`, `points_type`, `enabled`, `sort_order`)
VALUES
    (1, 'VIP', 'VIP', '#FFD700', '', 'glow', '', 10, 0, 1, 1),
    (2, 'Legend', 'Legend', '#8B5CF6', '', 'discord-neon', '', 15, 0, 1, 2),
    (3, 'Staff Pick', 'Staff', '#3B82F6', '*', 'cartoon', '', 20, 0, 1, 3);

CREATE TABLE IF NOT EXISTS `custom_nick_icons_catalog` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `icon_key` VARCHAR(50) NOT NULL,
    `display_name` VARCHAR(100) NOT NULL DEFAULT '',
    `points` INT(11) NOT NULL DEFAULT 0,
    `points_type` INT(11) NOT NULL DEFAULT 0,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_custom_nick_icons_catalog_icon_key` (`icon_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_nick_icons` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `user_id` INT(11) NOT NULL,
    `icon_key` VARCHAR(50) NOT NULL,
    `active` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_nick_icons_user_icon` (`user_id`, `icon_key`),
    KEY `idx_user_nick_icons_user_id` (`user_id`),
    KEY `idx_user_nick_icons_user_active` (`user_id`, `active`),
    CONSTRAINT `fk_user_nick_icons_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `custom_nick_icons_catalog` (`icon_key`, `display_name`, `points`, `points_type`, `enabled`, `sort_order`) VALUES
    ('1', 'Icon 1', 10, 0, 1, 1),
    ('2', 'Icon 2', 10, 0, 1, 2),
    ('3', 'Icon 3', 10, 0, 1, 3),
    ('4', 'Icon 4', 10, 0, 1, 4),
    ('5', 'Icon 5', 10, 0, 1, 5),
    ('6', 'Icon 6', 10, 0, 1, 6);

-- ------------------------------------------------------------
-- Custom badge maker
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `users_custom_badge_settings` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `badge_path` VARCHAR(255) NOT NULL DEFAULT '/var/www/gamedata/c_images/album1584',
    `badge_url` VARCHAR(255) NOT NULL DEFAULT '/gamedata/c_images/album1584',
    `price_badge` INT(11) NOT NULL DEFAULT 0,
    `currency_type` INT(11) NOT NULL DEFAULT -1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

INSERT INTO `users_custom_badge_settings` (`id`, `badge_path`, `badge_url`, `price_badge`, `currency_type`)
SELECT 1, '/var/www/gamedata/c_images/album1584', '/gamedata/c_images/album1584', 50, 5
WHERE NOT EXISTS (SELECT 1 FROM `users_custom_badge_settings` WHERE `id` = 1);

CREATE TABLE IF NOT EXISTS `user_custom_badge` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `user_id` INT(11) NOT NULL,
    `badge_id` VARCHAR(64) NOT NULL,
    `badge_name` VARCHAR(64) NOT NULL DEFAULT '',
    `badge_description` VARCHAR(255) NOT NULL DEFAULT '',
    `date_created` INT(11) NOT NULL DEFAULT 0,
    `date_edit` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_custom_badge_badge_id` (`badge_id`),
    KEY `idx_user_custom_badge_user_id` (`user_id`),
    CONSTRAINT `fk_user_custom_badge_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

-- ------------------------------------------------------------
-- UI/catalog compatibility values used by the current client
-- ------------------------------------------------------------

INSERT INTO `chat_bubbles` (`type`, `name`, `permission`, `overridable`, `triggers_talking_furniture`) VALUES
    (200, 'SHOW_MESSAGE_RED', '', 1, 0),
    (201, 'SHOW_MESSAGE_GREEN', '', 1, 0),
    (202, 'SHOW_MESSAGE_BLUE', '', 1, 0),
    (210, 'SHOW_MESSAGE_ALERT', '', 1, 0),
    (211, 'SHOW_MESSAGE_INFO', '', 1, 0),
    (212, 'SHOW_MESSAGE_WARNING', '', 1, 0),
    (220, 'SHOW_MESSAGE_WRONG', '', 1, 0),
    (221, 'SHOW_MESSAGE_WRONG_CIRCLED', '', 1, 0),
    (222, 'SHOW_MESSAGE_CORRECT', '', 1, 0),
    (223, 'SHOW_MESSAGE_CORRECT_CIRCLED', '', 1, 0),
    (224, 'SHOW_MESSAGE_QUESTION', '', 1, 0),
    (225, 'SHOW_MESSAGE_QUESTION_CIRCLED', '', 1, 0),
    (226, 'SHOW_MESSAGE_ARROW_UP', '', 1, 0),
    (227, 'SHOW_MESSAGE_ARROW_UP_CIRCLED', '', 1, 0),
    (228, 'SHOW_MESSAGE_ARROW_DOWN', '', 1, 0),
    (229, 'SHOW_MESSAGE_ARROW_DOWN_CIRCLED', '', 1, 0),
    (250, 'SHOW_MESSAGE_SKULL', '', 1, 0),
    (251, 'SHOW_MESSAGE_SKULL_ALT', '', 1, 0),
    (252, 'SHOW_MESSAGE_MAGNIFIER', '', 1, 0)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `overridable` = VALUES(`overridable`),
    `triggers_talking_furniture` = VALUES(`triggers_talking_furniture`);

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.acc_modtool_room_info', 'Allows viewing room information in the moderation tool.'),
    ('commands.description.cmd_add_youtube_playlist', ':add_youtube <base_item_id> <youtube_playlist_id>'),
    ('commands.description.cmd_disablemassmentions', ':disablemassmentions'),
    ('commands.description.cmd_disablementions', ':disablementions'),
    ('commands.description.cmd_give_prefix', ':giveprefix <username> <text> <color> [icon] [effect]'),
    ('commands.description.cmd_hidewired', ':hidewired'),
    ('commands.description.cmd_list_prefixes', ':listprefixes <username>'),
    ('commands.description.cmd_remove_prefix', ':removeprefix <username> <id|all>'),
    ('commands.description.cmd_setroom_template', ':setroom_template'),
    ('commands.description.cmd_update_youtube_playlists', ':update_youtube'),
    ('commands.keys.cmd_setroom_template', 'setroom_template;set_room_template'),
    ('commands.succes.cmd_setroom_template.verify', 'Copy the current room "%roomname%" to room_templates? Type :setroom_template %generic.yes% to confirm.'),
    ('commands.succes.cmd_setroom_template', 'Room saved as template id %id% with %items% items (%skipped% skipped - item_id not in items_base).'),
    ('commands.error.cmd_setroom_template', 'Could not save room as template. Check the server log for details.'),
    ('commands.error.cmd_setroom_template.no_room', 'You must be inside a room to use this command.'),
    ('commands.keys.cmd_give_prefix', 'giveprefix'),
    ('commands.keys.cmd_list_prefixes', 'listprefixes'),
    ('commands.keys.cmd_remove_prefix', 'removeprefix'),
    ('commands.keys.cmd_prefix_blacklist', 'prefixblacklist'),
    ('wiredfurni.badgereceived.body', 'You have just received a new Badge! Check your Inventory!'),
    ('wiredfurni.badgereceived.title', 'Badge received!');

-- Optional permission metadata for normalized permission schemas.
-- Actual rank values still belong in the permissions/permission_ranks setup.
CREATE TABLE IF NOT EXISTS `permission_definitions` (
    `permission_key` VARCHAR(64) NOT NULL,
    `max_value` TINYINT(3) UNSIGNED NOT NULL DEFAULT 1,
    `comment` TEXT NOT NULL,
    PRIMARY KEY (`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_setroom_template', 1, 'Allows using :setroom_template to copy a room into the login room-template table.'),
    ('cmd_give_prefix', 1, 'Allows granting custom prefixes to users.'),
    ('cmd_list_prefixes', 1, 'Allows listing custom prefixes assigned to users.'),
    ('cmd_remove_prefix', 1, 'Allows removing custom prefixes from users.'),
    ('cmd_prefix_blacklist', 1, 'Allows managing the custom prefix blacklist.')
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`);

-- ------------------------------------------------------------
-- Explicitly obsolete table from older remember-me attempts.
-- The current Java uses users_remember_families only.
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `users_remember_tokens`;
