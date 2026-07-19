-- Tables Polaris adds to Arcturus 3.5.5 installations (additive).
-- Names are trustworthy new objects, so a plain CREATE TABLE IF NOT EXISTS is safe.
SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE IF NOT EXISTS `builders_club_items` (
  `item_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`item_id`),
  KEY `idx_builders_club_items_user_id` (`user_id`),
  KEY `idx_builders_club_items_room_id` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

CREATE TABLE IF NOT EXISTS `chat_bubbles` (
  `type` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Only 46 and higher will work',
  `name` varchar(255) NOT NULL DEFAULT '',
  `permission` varchar(255) NOT NULL DEFAULT '',
  `overridable` tinyint(1) NOT NULL DEFAULT 1,
  `triggers_talking_furniture` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=253 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `custom_nick_icons_catalog` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `icon_key` varchar(50) NOT NULL,
  `display_name` varchar(100) NOT NULL DEFAULT '',
  `points` int(11) NOT NULL DEFAULT 0,
  `points_type` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_icon_key` (`icon_key`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `custom_prefixes_catalog` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `display_name` varchar(100) NOT NULL DEFAULT '',
  `text` varchar(50) NOT NULL,
  `color` varchar(255) NOT NULL DEFAULT '#FFFFFF',
  `icon` varchar(50) NOT NULL DEFAULT '',
  `effect` varchar(50) NOT NULL DEFAULT '',
  `font` varchar(50) NOT NULL DEFAULT '',
  `points` int(11) NOT NULL DEFAULT 0,
  `points_type` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `custom_prefix_blacklist` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `word` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `custom_prefix_settings` (
  `key_name` varchar(100) NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `infostand_backgrounds` (
  `id` int(11) NOT NULL,
  `category` enum('background','stand','overlay','card') NOT NULL,
  `min_rank` int(11) NOT NULL DEFAULT 0,
  `is_hc_only` tinyint(1) NOT NULL DEFAULT 0,
  `is_ambassador_only` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`,`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;

CREATE TABLE IF NOT EXISTS `password_resets` (
  `user_id` int(11) NOT NULL,
  `token` varchar(128) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `created_ip` varchar(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `idx_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `permission_definitions` (
  `permission_key` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `max_value` tinyint(3) unsigned NOT NULL DEFAULT 1,
  `comment` text NOT NULL,
  `rank_1` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rank_2` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rank_3` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rank_4` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rank_5` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rank_6` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rank_7` tinyint(3) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`permission_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_uca1400_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `permission_ranks` (
  `id` int(11) NOT NULL,
  `rank_name` varchar(25) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `hidden_rank` tinyint(1) NOT NULL DEFAULT 0,
  `badge` varchar(12) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '',
  `job_description` varchar(255) NOT NULL DEFAULT 'Here to help',
  `staff_color` varchar(8) NOT NULL DEFAULT '#327fa8',
  `staff_background` varchar(255) NOT NULL DEFAULT 'staff-bg.png',
  `level` int(11) NOT NULL DEFAULT 1,
  `room_effect` int(11) NOT NULL DEFAULT 0,
  `log_commands` enum('0','1') CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0',
  `prefix` varchar(5) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '',
  `prefix_color` varchar(7) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '',
  `auto_credits_amount` int(11) DEFAULT 0,
  `auto_pixels_amount` int(11) DEFAULT 0,
  `auto_gotw_amount` int(11) DEFAULT 0,
  `auto_points_amount` int(11) DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_uca1400_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `room_furni_wired_variables` (
  `room_id` int(11) NOT NULL,
  `furni_id` int(11) NOT NULL,
  `variable_item_id` int(11) NOT NULL,
  `value` int(11) DEFAULT NULL,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`room_id`,`furni_id`,`variable_item_id`),
  KEY `idx_room_furni_wired_variables_room_item` (`room_id`,`variable_item_id`),
  KEY `idx_room_furni_wired_variables_furni` (`furni_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_templates` (
  `template_id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(128) NOT NULL DEFAULT '',
  `description` varchar(256) NOT NULL DEFAULT '',
  `thumbnail` varchar(512) NOT NULL DEFAULT '',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  `name` varchar(50) NOT NULL DEFAULT '',
  `room_description` varchar(250) NOT NULL DEFAULT '',
  `model` varchar(100) NOT NULL,
  `password` varchar(50) NOT NULL DEFAULT '',
  `state` enum('open','locked','password','invisible') NOT NULL DEFAULT 'open',
  `users_max` int(11) NOT NULL DEFAULT 25,
  `category` int(11) NOT NULL DEFAULT 0,
  `paper_floor` varchar(50) NOT NULL DEFAULT '0.0',
  `paper_wall` varchar(50) NOT NULL DEFAULT '0.0',
  `paper_landscape` varchar(50) NOT NULL DEFAULT '0.0',
  `thickness_wall` int(11) NOT NULL DEFAULT 0,
  `thickness_floor` int(11) NOT NULL DEFAULT 0,
  `moodlight_data` varchar(2048) NOT NULL DEFAULT '',
  `override_model` enum('0','1') NOT NULL DEFAULT '0',
  `trade_mode` int(2) NOT NULL DEFAULT 2,
  `heightmap` mediumtext NOT NULL DEFAULT '',
  `door_x` int(11) NOT NULL DEFAULT 0,
  `door_y` int(11) NOT NULL DEFAULT 0,
  `door_dir` int(4) NOT NULL DEFAULT 2,
  PRIMARY KEY (`template_id`),
  KEY `enabled_sort` (`enabled`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `room_templates_items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `template_id` int(11) NOT NULL,
  `item_id` int(11) unsigned NOT NULL,
  `wall_pos` varchar(20) NOT NULL DEFAULT '',
  `x` int(11) NOT NULL DEFAULT 0,
  `y` int(11) NOT NULL DEFAULT 0,
  `z` double(10,6) NOT NULL DEFAULT 0.000000,
  `rot` int(11) NOT NULL DEFAULT 0,
  `extra_data` varchar(2096) NOT NULL DEFAULT '',
  `wired_data` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `template_id` (`template_id`),
  KEY `fk_rt_items_item_base` (`item_id`),
  CONSTRAINT `fk_rt_items_item_base` FOREIGN KEY (`item_id`) REFERENCES `items_base` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rt_items_template` FOREIGN KEY (`template_id`) REFERENCES `room_templates` (`template_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `room_user_wired_variables` (
  `room_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `variable_item_id` int(11) NOT NULL,
  `value` int(11) DEFAULT NULL,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`room_id`,`user_id`,`variable_item_id`),
  KEY `idx_room_user_wired_variables_room_item` (`room_id`,`variable_item_id`),
  KEY `idx_room_user_wired_variables_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_wired_settings` (
  `room_id` int(11) NOT NULL,
  `inspect_mask` int(11) NOT NULL DEFAULT 0 COMMENT 'Bitmask for who can open and inspect Wired in the room. 1=everyone, 2=users with rights, 4=group members, 8=group admins.',
  `modify_mask` int(11) NOT NULL DEFAULT 0 COMMENT 'Bitmask for who can modify Wired in the room. 2=users with rights, 4=group members, 8=group admins.',
  PRIMARY KEY (`room_id`),
  CONSTRAINT `fk_room_wired_settings_room_id` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_wired_variables` (
  `room_id` int(11) NOT NULL,
  `variable_item_id` int(11) NOT NULL,
  `value` int(11) NOT NULL DEFAULT 0,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`room_id`,`variable_item_id`),
  KEY `idx_room_wired_variables_room_item` (`room_id`,`variable_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_custom_badge_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `badge_path` varchar(255) NOT NULL DEFAULT '/var/www/gamedata/c_images/album1584',
  `badge_url` varchar(255) NOT NULL DEFAULT '/gamedata/c_images/album1584',
  `price_badge` int(11) NOT NULL DEFAULT 0,
  `currency_type` int(11) NOT NULL DEFAULT -1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users_remember_families` (
  `family_id` char(36) NOT NULL,
  `user_id` int(11) NOT NULL,
  `current_version` int(11) NOT NULL DEFAULT 1,
  `created_at` int(11) NOT NULL,
  `expires_at` int(11) NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT 0,
  `last_ip` varchar(45) NOT NULL DEFAULT '',
  PRIMARY KEY (`family_id`),
  KEY `user_id` (`user_id`),
  KEY `expires_at` (`expires_at`),
  CONSTRAINT `fk_remember_family_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `user_custom_badge` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `badge_id` varchar(64) NOT NULL,
  `badge_name` varchar(64) NOT NULL DEFAULT '',
  `badge_description` varchar(255) NOT NULL DEFAULT '',
  `date_created` int(11) NOT NULL DEFAULT 0,
  `date_edit` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `badge_id` (`badge_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `fk_user_custom_badge_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `user_nick_icons` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `icon_key` varchar(50) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_icon` (`user_id`,`icon_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_active` (`user_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `user_prefixes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `text` varchar(50) NOT NULL,
  `color` varchar(255) NOT NULL DEFAULT '#FFFFFF',
  `icon` varchar(50) NOT NULL DEFAULT '',
  `effect` varchar(50) NOT NULL DEFAULT '',
  `font` varchar(50) NOT NULL DEFAULT '',
  `catalog_prefix_id` int(11) NOT NULL DEFAULT 0,
  `display_name` varchar(100) NOT NULL DEFAULT '',
  `points` int(11) NOT NULL DEFAULT 0,
  `points_type` int(11) NOT NULL DEFAULT 0,
  `is_custom` tinyint(1) NOT NULL DEFAULT 1,
  `active` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_active` (`user_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_visual_settings` (
  `user_id` int(11) NOT NULL,
  `display_order` varchar(50) NOT NULL DEFAULT 'icon-prefix-name',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE IF NOT EXISTS `wired_emulator_settings` (
  `key` varchar(191) NOT NULL,
  `value` text NOT NULL,
  `comment` text NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

SET FOREIGN_KEY_CHECKS=1;
