-- Arcturus Morningstar 3.5.5 schema fixture.
-- Only legacy permission rows are retained; hotel data is layered on separately.
-- Generated from BaseDB MS 3.5.5.sql; obsolete old_guilds_forums* excluded.
SET FOREIGN_KEY_CHECKS=0;
CREATE TABLE IF NOT EXISTS `achievements` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL DEFAULT 'ACH_',
  `category` enum('identity','explore','music','social','games','room_builder','pets','tools','events') NOT NULL DEFAULT 'identity',
  `level` int(11) NOT NULL DEFAULT 1,
  `reward_amount` int(11) NOT NULL DEFAULT 100,
  `reward_type` int(11) NOT NULL DEFAULT 0,
  `points` int(11) DEFAULT 10,
  `progress_needed` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`name`,`level`) USING BTREE,
  UNIQUE KEY `id` (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=2762 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `achievements_talents` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` enum('citizenship','helper') NOT NULL DEFAULT 'citizenship',
  `level` int(11) NOT NULL DEFAULT 0,
  `achievement_ids` varchar(100) NOT NULL DEFAULT '',
  `achievement_levels` varchar(100) NOT NULL DEFAULT '',
  `reward_furni` varchar(100) NOT NULL DEFAULT '',
  `reward_perks` varchar(100) NOT NULL DEFAULT '',
  `reward_badges` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `bans` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `ip` varchar(50) NOT NULL DEFAULT '',
  `machine_id` varchar(255) NOT NULL DEFAULT '',
  `user_staff_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL,
  `ban_expire` int(11) NOT NULL DEFAULT 0,
  `ban_reason` varchar(200) NOT NULL DEFAULT '',
  `type` enum('account','ip','machine','super') NOT NULL DEFAULT 'account' COMMENT 'Account is the entry in the users table banned.\nIP is any client that connects with that IP.\nMachine is the computer that logged in.\nSuper is all of the above.',
  `cfh_topic` int(11) NOT NULL DEFAULT -1,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `user_data` (`user_id`,`ip`,`machine_id`,`ban_expire`,`timestamp`,`ban_reason`) USING BTREE,
  KEY `general` (`id`,`type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `bot_serves` (
  `keys` varchar(128) NOT NULL,
  `item` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `bots` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `room_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(25) NOT NULL DEFAULT '',
  `motto` varchar(100) NOT NULL DEFAULT '',
  `figure` varchar(500) NOT NULL DEFAULT '',
  `gender` enum('M','F') NOT NULL DEFAULT 'M',
  `x` int(11) NOT NULL DEFAULT 0,
  `y` int(11) NOT NULL DEFAULT 0,
  `z` double(11,1) NOT NULL DEFAULT 0.0,
  `rot` int(11) NOT NULL DEFAULT 0,
  `chat_lines` varchar(5112) NOT NULL DEFAULT '',
  `chat_auto` enum('0','1') NOT NULL DEFAULT '1',
  `chat_random` enum('0','1') NOT NULL DEFAULT '1',
  `chat_delay` int(11) NOT NULL DEFAULT 5,
  `dance` int(11) NOT NULL DEFAULT 0,
  `freeroam` enum('0','1') NOT NULL DEFAULT '0',
  `type` enum('generic','visitor_log','bartender','weapons_dealer') NOT NULL DEFAULT 'generic',
  `effect` int(11) NOT NULL DEFAULT 0,
  `bubble_id` int(11) DEFAULT 31,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `general_data` (`id`,`user_id`,`room_id`,`name`,`motto`,`gender`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `calendar_campaigns` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '',
  `image` varchar(255) NOT NULL DEFAULT '',
  `start_timestamp` int(11) NOT NULL DEFAULT 0,
  `total_days` int(11) NOT NULL DEFAULT 30,
  `lock_expired` enum('1','0') NOT NULL DEFAULT '1',
  `enabled` enum('1','0') NOT NULL DEFAULT '1',
  UNIQUE KEY `id` (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `calendar_rewards` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `campaign_id` int(11) NOT NULL DEFAULT 0,
  `product_name` varchar(128) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
  `custom_image` varchar(128) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
  `credits` int(11) NOT NULL DEFAULT 0,
  `pixels` int(11) NOT NULL DEFAULT 0,
  `points` int(11) NOT NULL DEFAULT 0,
  `points_type` int(11) NOT NULL DEFAULT 0,
  `badge` varchar(25) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
  `item_id` int(11) NOT NULL DEFAULT 0,
  `subscription_type` varchar(128) CHARACTER SET latin1 COLLATE latin1_swedish_ci DEFAULT '',
  `subscription_days` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `calendar_rewards_claimed` (
  `user_id` int(11) NOT NULL,
  `campaign_id` int(11) NOT NULL DEFAULT 0,
  `day` int(11) NOT NULL,
  `reward_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `camera_web` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL DEFAULT 0,
  `timestamp` int(11) NOT NULL,
  `url` varchar(128) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `id` (`id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_clothing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(75) NOT NULL,
  `setid` varchar(75) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=845 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_club_offers` (
  `id` int(11) NOT NULL,
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  `name` varchar(35) NOT NULL,
  `days` int(11) NOT NULL,
  `credits` int(11) NOT NULL DEFAULT 10,
  `points` int(11) NOT NULL DEFAULT 0,
  `points_type` int(11) NOT NULL DEFAULT 0,
  `type` enum('HC','VIP') NOT NULL DEFAULT 'HC',
  `deal` enum('0','1') NOT NULL DEFAULT '0',
  `giftable` enum('1','0') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_featured_pages` (
  `slot_id` int(11) NOT NULL,
  `image` varchar(70) NOT NULL DEFAULT '',
  `caption` varchar(130) NOT NULL DEFAULT '',
  `type` enum('page_name','page_id','product_name') NOT NULL DEFAULT 'page_name',
  `expire_timestamp` int(11) NOT NULL DEFAULT -1,
  `page_name` varchar(30) NOT NULL DEFAULT '',
  `page_id` int(11) NOT NULL DEFAULT 0,
  `product_name` varchar(40) NOT NULL DEFAULT '',
  PRIMARY KEY (`slot_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_ids` varchar(666) NOT NULL,
  `page_id` int(11) NOT NULL,
  `catalog_name` varchar(100) NOT NULL,
  `cost_credits` int(11) NOT NULL DEFAULT 3,
  `cost_points` int(11) NOT NULL DEFAULT 0,
  `points_type` int(11) NOT NULL DEFAULT 0 COMMENT '0 for duckets; 5 for diamonds; and any seasonal/GOTW currencies you have in your emu_settings table.',
  `amount` int(11) NOT NULL DEFAULT 1,
  `limited_stack` int(11) NOT NULL DEFAULT 0 COMMENT 'Change this number to make the item limited.',
  `limited_sells` int(11) NOT NULL DEFAULT 0 COMMENT 'This automatically logs from the emu; do not change it.',
  `order_number` int(11) NOT NULL DEFAULT 1,
  `offer_id` int(11) NOT NULL DEFAULT -1,
  `song_id` int(10) unsigned NOT NULL DEFAULT 0,
  `extradata` varchar(500) NOT NULL DEFAULT '',
  `have_offer` enum('0','1') NOT NULL DEFAULT '1',
  `club_only` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `page_id` (`page_id`) USING BTREE,
  KEY `catalog_name` (`catalog_name`) USING BTREE,
  KEY `costs` (`cost_credits`,`cost_points`,`points_type`) USING BTREE,
  KEY `id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20501 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_items_bc` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_ids` varchar(666) NOT NULL,
  `page_id` int(11) NOT NULL,
  `catalog_name` varchar(100) NOT NULL,
  `order_number` int(11) NOT NULL DEFAULT 1,
  `extradata` varchar(500) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_items_limited` (
  `catalog_item_id` int(11) NOT NULL,
  `number` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `timestamp` int(11) NOT NULL DEFAULT 0,
  `item_id` int(11) NOT NULL DEFAULT 0,
  UNIQUE KEY `catalog_item_id` (`catalog_item_id`,`number`) USING BTREE,
  KEY `user_timestamp_index` (`user_id`,`timestamp`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_pages` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) NOT NULL DEFAULT -1,
  `caption_save` varchar(25) NOT NULL DEFAULT '',
  `caption` varchar(128) NOT NULL,
  `page_layout` enum('default_3x3','club_buy','club_gift','frontpage','spaces','recycler','recycler_info','recycler_prizes','trophies','plasto','marketplace','marketplace_own_items','spaces_new','soundmachine','guilds','guild_furni','info_duckets','info_rentables','info_pets','roomads','single_bundle','sold_ltd_items','badge_display','bots','pets','pets2','pets3','productpage1','room_bundle','recent_purchases','default_3x3_color_grouping','guild_forum','vip_buy','info_loyalty','loyalty_vip_buy','collectibles','petcustomization','frontpage_featured') NOT NULL DEFAULT 'default_3x3',
  `icon_color` int(11) NOT NULL DEFAULT 1,
  `icon_image` int(11) NOT NULL DEFAULT 1,
  `min_rank` int(11) NOT NULL DEFAULT 1,
  `order_num` int(11) NOT NULL DEFAULT 1,
  `visible` enum('0','1') NOT NULL DEFAULT '1',
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  `club_only` enum('0','1') NOT NULL DEFAULT '0',
  `vip_only` enum('1','0') NOT NULL DEFAULT '0',
  `page_headline` varchar(1024) NOT NULL DEFAULT '',
  `page_teaser` varchar(64) NOT NULL DEFAULT '',
  `page_special` varchar(2048) DEFAULT '' COMMENT 'Gold Bubble: catalog_special_txtbg1 // Speech Bubble: catalog_special_txtbg2 // Place normal text in page_text_teaser',
  `page_text1` text DEFAULT NULL,
  `page_text2` text DEFAULT NULL,
  `page_text_details` text DEFAULT NULL,
  `page_text_teaser` text DEFAULT NULL,
  `room_id` int(11) DEFAULT 0,
  `includes` varchar(128) NOT NULL DEFAULT '' COMMENT 'Example usage: 1;2;3\r\n This will include page 1, 2 and 3 in the current page.\r\n Note that permissions are only used for the current entry.',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `id` (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=1111 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_pages_bc` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) NOT NULL DEFAULT -1,
  `caption` varchar(128) NOT NULL,
  `page_layout` enum('default_3x3','club_buy','club_gift','frontpage','spaces','recycler','recycler_info','recycler_prizes','trophies','plasto','marketplace','marketplace_own_items','spaces_new','soundmachine','guilds','guild_furni','info_duckets','info_rentables','info_pets','roomads','single_bundle','sold_ltd_items','badge_display','bots','pets','pets2','pets3','productpage1','room_bundle','recent_purchases','default_3x3_color_grouping','guild_forum','vip_buy','info_loyalty','loyalty_vip_buy','collectibles','petcustomization','frontpage_featured') NOT NULL DEFAULT 'default_3x3',
  `icon_color` int(11) NOT NULL DEFAULT 1,
  `icon_image` int(11) NOT NULL DEFAULT 1,
  `order_num` int(11) NOT NULL DEFAULT 1,
  `visible` enum('0','1') NOT NULL DEFAULT '1',
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  `page_headline` varchar(1024) NOT NULL DEFAULT '',
  `page_teaser` varchar(64) NOT NULL DEFAULT '',
  `page_special` varchar(2048) DEFAULT '' COMMENT 'Gold Bubble: catalog_special_txtbg1 // Speech Bubble: catalog_special_txtbg2 // Place normal text in page_text_teaser',
  `page_text1` text DEFAULT NULL,
  `page_text2` text DEFAULT NULL,
  `page_text_details` text DEFAULT NULL,
  `page_text_teaser` text DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=3 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `catalog_target_offers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `offer_code` varchar(32) NOT NULL,
  `title` varchar(128) NOT NULL DEFAULT '',
  `description` varchar(2048) NOT NULL DEFAULT '',
  `image` varchar(128) NOT NULL,
  `icon` varchar(128) NOT NULL,
  `end_timestamp` int(11) NOT NULL,
  `credits` int(11) NOT NULL DEFAULT 10,
  `points` int(11) NOT NULL DEFAULT 10,
  `points_type` int(11) NOT NULL DEFAULT 5,
  `purchase_limit` int(11) NOT NULL DEFAULT 5,
  `catalog_item` int(11) NOT NULL,
  `vars` varchar(1024) NOT NULL DEFAULT '' COMMENT 'List of strings seperated by a ;',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `chatlogs_private` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_from_id` int(11) NOT NULL,
  `user_to_id` int(11) NOT NULL,
  `message` varchar(255) NOT NULL,
  `timestamp` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `id` (`id`) USING BTREE,
  KEY `user_from_id` (`user_from_id`) USING BTREE,
  KEY `user_to_id` (`user_to_id`) USING BTREE,
  KEY `message` (`message`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `chatlogs_room` (
  `room_id` int(11) NOT NULL DEFAULT 0,
  `user_from_id` int(11) NOT NULL,
  `user_to_id` int(11) NOT NULL DEFAULT 0,
  `message` varchar(255) NOT NULL,
  `timestamp` int(11) NOT NULL,
  KEY `user_from_id` (`user_from_id`) USING BTREE,
  KEY `timestamp` (`timestamp`) USING BTREE,
  KEY `user_to_id` (`user_to_id`) USING BTREE,
  KEY `message` (`message`) USING BTREE,
  KEY `room_id` (`room_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `commandlogs` (
  `user_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL,
  `command` varchar(256) NOT NULL DEFAULT '',
  `params` varchar(256) NOT NULL DEFAULT '',
  `succes` enum('no','yes') NOT NULL DEFAULT 'yes',
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `user_data` (`user_id`,`timestamp`) USING BTREE,
  KEY `command` (`command`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `crafting_altars_recipes` (
  `altar_id` int(11) NOT NULL,
  `recipe_id` int(11) NOT NULL,
  UNIQUE KEY `altar_id` (`altar_id`,`recipe_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `crafting_recipes` (
  `id` int(11) NOT NULL,
  `product_name` varchar(64) NOT NULL COMMENT 'WARNING! This field must match a entry in your productdata or crafting WILL NOT WORK!',
  `reward` int(11) NOT NULL,
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  `achievement` varchar(255) NOT NULL DEFAULT '',
  `secret` enum('0','1') NOT NULL DEFAULT '0',
  `limited` enum('0','1') NOT NULL DEFAULT '0',
  `remaining` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `id` (`id`) USING BTREE,
  UNIQUE KEY `name` (`product_name`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `crafting_recipes_ingredients` (
  `recipe_id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `amount` int(11) NOT NULL DEFAULT 1
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `emulator_errors` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `timestamp` int(11) NOT NULL DEFAULT 0,
  `version` varchar(64) NOT NULL,
  `build_hash` varchar(64) NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT 'Exception',
  `stacktrace` blob NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `emulator_settings` (
  `key` varchar(100) NOT NULL,
  `value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`key`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `emulator_texts` (
  `key` varchar(100) NOT NULL,
  `value` varchar(4096) NOT NULL,
  PRIMARY KEY (`key`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `gift_wrappers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sprite_id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `type` enum('gift','wrapper') NOT NULL DEFAULT 'wrapper',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=18 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `groups_items` (
  `type` enum('base','symbol','color','color2','color3') NOT NULL,
  `id` int(11) NOT NULL,
  `firstvalue` varchar(255) NOT NULL,
  `secondvalue` varchar(2000) NOT NULL,
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`,`type`) USING BTREE,
  KEY `type` (`type`) USING BTREE,
  KEY `id` (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `guild_forum_views` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `guild_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `guilds` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(50) NOT NULL DEFAULT '',
  `description` varchar(250) NOT NULL DEFAULT '',
  `room_id` int(11) NOT NULL DEFAULT 0,
  `state` int(11) NOT NULL DEFAULT 0,
  `rights` enum('0','1') NOT NULL DEFAULT '0',
  `color_one` int(11) NOT NULL DEFAULT 0,
  `color_two` int(11) NOT NULL DEFAULT 0,
  `badge` varchar(256) NOT NULL DEFAULT '',
  `date_created` int(11) NOT NULL,
  `forum` enum('0','1') NOT NULL DEFAULT '0',
  `read_forum` enum('EVERYONE','MEMBERS','ADMINS') NOT NULL DEFAULT 'EVERYONE',
  `post_messages` enum('EVERYONE','MEMBERS','ADMINS','OWNER') NOT NULL DEFAULT 'EVERYONE',
  `post_threads` enum('EVERYONE','MEMBERS','ADMINS','OWNER') NOT NULL DEFAULT 'EVERYONE',
  `mod_forum` enum('ADMINS','OWNER') NOT NULL DEFAULT 'ADMINS',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `id` (`id`) USING BTREE,
  KEY `data` (`room_id`,`user_id`) USING BTREE,
  KEY `name` (`name`) USING BTREE,
  KEY `description` (`description`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `guilds_elements` (
  `id` int(11) NOT NULL,
  `firstvalue` varchar(300) NOT NULL,
  `secondvalue` varchar(300) NOT NULL,
  `type` varchar(50) NOT NULL,
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  UNIQUE KEY `id` (`id`,`type`) USING BTREE,
  UNIQUE KEY `data` (`id`,`type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `guilds_forums_comments` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `message` text NOT NULL,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `state` int(11) NOT NULL DEFAULT 0,
  `admin_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `id` (`id`) USING BTREE,
  KEY `thread_data` (`thread_id`,`user_id`,`created_at`,`state`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT;
COMMIT;
CREATE TABLE IF NOT EXISTS `guilds_forums_threads` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `guild_id` int(11) DEFAULT 0,
  `opener_id` int(11) DEFAULT 0,
  `subject` varchar(255) DEFAULT '',
  `posts_count` int(11) DEFAULT 0,
  `created_at` int(11) DEFAULT 0,
  `updated_at` int(11) DEFAULT 0,
  `state` int(11) DEFAULT 0,
  `pinned` tinyint(4) DEFAULT 0,
  `locked` tinyint(4) DEFAULT 0,
  `admin_id` int(11) DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=COMPACT;
COMMIT;
CREATE TABLE IF NOT EXISTS `guilds_members` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `guild_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `level_id` int(11) NOT NULL DEFAULT 0,
  `member_since` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `id` (`id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `guild_id` (`guild_id`) USING BTREE,
  KEY `userdata` (`user_id`,`guild_id`) USING BTREE,
  KEY `level_id` (`level_id`) USING BTREE,
  KEY `member_since` (`member_since`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `hotelview_news` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL,
  `text` varchar(500) NOT NULL,
  `button_text` varchar(50) NOT NULL,
  `button_type` enum('client','web') NOT NULL DEFAULT 'web',
  `button_link` varchar(200) NOT NULL,
  `image` varchar(200) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `room_id` int(11) NOT NULL DEFAULT 0,
  `item_id` int(11) DEFAULT 0,
  `wall_pos` varchar(20) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
  `x` int(11) NOT NULL DEFAULT 0,
  `y` int(11) NOT NULL DEFAULT 0,
  `z` double(10,6) NOT NULL DEFAULT 0.000000,
  `rot` int(11) NOT NULL DEFAULT 0,
  `extra_data` varchar(1024) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
  `wired_data` varchar(10000) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
  `limited_data` varchar(10) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '0:0',
  `guild_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `room_id` (`user_id`,`room_id`) USING BTREE,
  KEY `itemsdata` (`room_id`,`item_id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `extra_data` (`extra_data`) USING BTREE,
  KEY `wired_data` (`wired_data`(3072)) USING BTREE,
  KEY `id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=336 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `items_base` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `sprite_id` int(11) NOT NULL DEFAULT 0,
  `public_name` varchar(56) NOT NULL DEFAULT '',
  `item_name` varchar(70) NOT NULL,
  `type` varchar(3) NOT NULL DEFAULT 's',
  `width` int(11) NOT NULL DEFAULT 1,
  `length` int(11) NOT NULL DEFAULT 1,
  `stack_height` double(4,2) NOT NULL DEFAULT 0.00,
  `allow_stack` tinyint(1) NOT NULL DEFAULT 1,
  `allow_sit` tinyint(1) NOT NULL DEFAULT 0,
  `allow_lay` tinyint(1) NOT NULL DEFAULT 0,
  `allow_walk` tinyint(1) NOT NULL DEFAULT 0,
  `allow_gift` tinyint(1) NOT NULL DEFAULT 1,
  `allow_trade` tinyint(1) NOT NULL DEFAULT 1,
  `allow_recycle` tinyint(1) NOT NULL DEFAULT 0,
  `allow_marketplace_sell` tinyint(1) NOT NULL DEFAULT 0,
  `allow_inventory_stack` tinyint(1) NOT NULL DEFAULT 1,
  `interaction_type` varchar(500) NOT NULL DEFAULT 'default',
  `interaction_modes_count` int(11) NOT NULL DEFAULT 1,
  `vending_ids` varchar(255) NOT NULL DEFAULT '0',
  `multiheight` varchar(50) NOT NULL DEFAULT '0',
  `customparams` varchar(256) NOT NULL DEFAULT '',
  `effect_id_male` int(11) NOT NULL DEFAULT 0,
  `effect_id_female` int(11) NOT NULL DEFAULT 0,
  `clothing_on_walk` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=50513 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `items_crackable` (
  `item_id` int(11) NOT NULL,
  `item_name` varchar(255) NOT NULL COMMENT 'Item name for identification',
  `count` int(11) NOT NULL,
  `prizes` varchar(255) NOT NULL DEFAULT '179:1' COMMENT 'Used in the format of item_id:chance;item_id_2:chance. item_id must be id in the items_base table. Default value for chance is 100.',
  `achievement_tick` varchar(64) NOT NULL,
  `achievement_cracked` varchar(64) NOT NULL,
  `required_effect` int(11) NOT NULL DEFAULT 0,
  `subscription_duration` int(11) DEFAULT NULL,
  `subscription_type` varchar(255) DEFAULT NULL COMMENT 'hc for Habbo Club, bc for Builders Club',
  PRIMARY KEY (`item_id`) USING BTREE,
  UNIQUE KEY `id` (`item_id`) USING BTREE,
  KEY `data` (`count`,`prizes`,`achievement_tick`,`achievement_cracked`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `items_highscore_data` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_id` int(11) NOT NULL,
  `user_ids` varchar(500) NOT NULL,
  `score` int(11) NOT NULL,
  `is_win` tinyint(1) DEFAULT 0,
  `timestamp` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `id` (`id`) USING BTREE,
  KEY `data` (`item_id`,`user_ids`) USING BTREE,
  KEY `status` (`is_win`,`score`,`timestamp`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `items_hoppers` (
  `item_id` int(11) NOT NULL,
  `base_item` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `items_presents` (
  `item_id` int(11) NOT NULL,
  `base_item_reward` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `items_teleports` (
  `teleport_one_id` int(11) NOT NULL,
  `teleport_two_id` int(11) NOT NULL,
  KEY `teleport_one_id` (`teleport_one_id`) USING BTREE,
  KEY `teleport_two_id` (`teleport_two_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `logs_hc_payday` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `timestamp` int(10) unsigned DEFAULT NULL,
  `user_id` int(10) unsigned DEFAULT NULL,
  `hc_streak` int(10) unsigned DEFAULT NULL,
  `total_coins_spent` int(10) unsigned DEFAULT NULL,
  `reward_coins_spent` int(10) unsigned DEFAULT NULL,
  `reward_streak` int(10) unsigned DEFAULT NULL,
  `total_payout` int(10) unsigned DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `claimed` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `timestamp` (`timestamp`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `logs_shop_purchases` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `timestamp` int(10) unsigned DEFAULT NULL,
  `user_id` int(10) unsigned DEFAULT NULL,
  `catalog_item_id` int(10) unsigned DEFAULT NULL,
  `item_ids` text DEFAULT NULL,
  `catalog_name` varchar(255) DEFAULT NULL,
  `cost_credits` int(11) DEFAULT NULL,
  `cost_points` int(11) DEFAULT NULL,
  `points_type` int(11) DEFAULT NULL,
  `amount` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `timestamp` (`timestamp`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `marketplace_items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `price` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL,
  `sold_timestamp` int(11) NOT NULL DEFAULT 0,
  `state` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `itemdata` (`item_id`,`user_id`) USING BTREE,
  KEY `price` (`price`) USING BTREE,
  KEY `time` (`timestamp`,`sold_timestamp`) USING BTREE,
  KEY `status` (`state`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `messenger_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(25) NOT NULL,
  `user_id` int(11) NOT NULL,
  UNIQUE KEY `identifier` (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `messenger_friendrequests` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_to_id` int(11) NOT NULL DEFAULT 0,
  `user_from_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `id` (`id`) USING BTREE,
  KEY `users` (`user_to_id`,`user_from_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `messenger_friendships` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_one_id` int(11) NOT NULL DEFAULT 0,
  `user_two_id` int(11) NOT NULL DEFAULT 0,
  `relation` int(11) NOT NULL DEFAULT 0,
  `friends_since` int(11) NOT NULL DEFAULT 0,
  `category` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `id` (`id`) USING BTREE,
  KEY `user_one_id` (`user_one_id`) USING BTREE,
  KEY `user_two_id` (`user_two_id`) USING BTREE,
  KEY `userdata` (`user_one_id`,`user_two_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `messenger_offline` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `user_from_id` int(11) NOT NULL DEFAULT 0,
  `message` varchar(500) NOT NULL,
  `sended_on` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `namechange_log` (
  `user_id` int(11) NOT NULL,
  `old_name` varchar(32) NOT NULL,
  `new_name` varchar(32) NOT NULL,
  `timestamp` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `navigator_filter` (
  `key` varchar(11) NOT NULL,
  `field` varchar(32) NOT NULL,
  `compare` enum('equals','equals_ignore_case','contains') NOT NULL,
  `database_query` varchar(1024) NOT NULL,
  PRIMARY KEY (`key`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `navigator_flatcats` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `min_rank` int(11) NOT NULL DEFAULT 0,
  `caption_save` varchar(32) NOT NULL DEFAULT 'caption_save',
  `caption` varchar(100) NOT NULL,
  `can_trade` enum('0','1') NOT NULL DEFAULT '1',
  `max_user_count` int(11) NOT NULL DEFAULT 100,
  `public` enum('0','1') NOT NULL DEFAULT '0',
  `list_type` int(11) NOT NULL DEFAULT 0 COMMENT 'Display mode in the navigator. 0 for list, 1 for thumbnails.',
  `order_num` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `navigator_publiccats` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL DEFAULT 'Staff Picks',
  `image` enum('0','1') NOT NULL DEFAULT '0',
  `visible` enum('0','1') NOT NULL DEFAULT '1',
  `order_num` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=7 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `navigator_publics` (
  `public_cat_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL,
  `visible` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `nux_gifts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` enum('item','room') NOT NULL DEFAULT 'item',
  `value` varchar(32) NOT NULL COMMENT 'If type item then items.item_name. If type room then room id to copy.',
  `image` varchar(256) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=4 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `permissions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rank_name` varchar(25) NOT NULL,
  `badge` varchar(12) NOT NULL DEFAULT '',
  `level` int(11) NOT NULL DEFAULT 1,
  `room_effect` int(11) NOT NULL DEFAULT 0,
  `log_commands` enum('0','1') NOT NULL DEFAULT '0',
  `prefix` varchar(5) NOT NULL,
  `prefix_color` varchar(7) NOT NULL,
  `cmd_about` enum('0','1') NOT NULL DEFAULT '1',
  `cmd_alert` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_allow_trading` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_badge` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_ban` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_blockalert` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_bots` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_bundle` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_calendar` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_changename` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_chatcolor` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_commands` enum('0','1') NOT NULL DEFAULT '1',
  `cmd_connect_camera` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_control` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_coords` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_credits` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_subscription` enum('0','1') DEFAULT '0',
  `cmd_danceall` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_diagonal` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_disconnect` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_duckets` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_ejectall` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_empty` enum('0','1') NOT NULL DEFAULT '1',
  `cmd_empty_bots` enum('0','1') NOT NULL DEFAULT '1',
  `cmd_empty_pets` enum('0','1') NOT NULL DEFAULT '1',
  `cmd_enable` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_event` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_faceless` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_fastwalk` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_filterword` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_freeze` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_freeze_bots` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_gift` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_give_rank` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_ha` enum('0','1') NOT NULL DEFAULT '0',
  `acc_can_stalk` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_hal` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_invisible` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_ip_ban` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_machine_ban` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_hand_item` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_happyhour` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_hidewired` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_kickall` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_softkick` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_massbadge` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_roombadge` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_masscredits` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_massduckets` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_massgift` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_masspoints` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_moonwalk` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_mimic` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_multi` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_mute` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_pet_info` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_pickall` enum('0','1') NOT NULL DEFAULT '1',
  `cmd_plugins` enum('0','1') NOT NULL DEFAULT '1',
  `cmd_points` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_promote_offer` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_pull` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_push` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_redeem` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_reload_room` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_roomalert` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_roomcredits` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_roomeffect` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_roomgift` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_roomitem` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_roommute` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_roompixels` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_roompoints` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_say` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_say_all` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_setmax` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_set_poll` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_setpublic` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_setspeed` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_shout` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_shout_all` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_shutdown` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_sitdown` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_staffalert` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_staffonline` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_summon` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_summonrank` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_super_ban` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_stalk` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_superpull` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_take_badge` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_talk` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_teleport` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_trash` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_transform` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_unban` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_unload` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_unmute` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_achievements` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_bots` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_catalogue` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_config` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_guildparts` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_hotel_view` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_items` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_navigator` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_permissions` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_pet_data` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_plugins` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_polls` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_texts` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_wordfilter` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_userinfo` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_word_quiz` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_warp` enum('0','1') NOT NULL DEFAULT '0',
  `acc_anychatcolor` enum('0','1') NOT NULL DEFAULT '0',
  `acc_anyroomowner` enum('0','1') NOT NULL DEFAULT '0',
  `acc_empty_others` enum('0','1') NOT NULL DEFAULT '0',
  `acc_enable_others` enum('0','1') NOT NULL DEFAULT '0',
  `acc_see_whispers` enum('0','1') NOT NULL DEFAULT '0',
  `acc_see_tentchat` enum('0','1') NOT NULL DEFAULT '0',
  `acc_superwired` enum('0','1') NOT NULL DEFAULT '0',
  `acc_supporttool` enum('0','1') NOT NULL DEFAULT '0',
  `acc_unkickable` enum('0','1') NOT NULL DEFAULT '0',
  `acc_guildgate` enum('0','1') NOT NULL DEFAULT '0',
  `acc_moverotate` enum('0','1') NOT NULL DEFAULT '0',
  `acc_placefurni` enum('0','1') NOT NULL DEFAULT '0',
  `acc_unlimited_bots` enum('0','1','2') NOT NULL DEFAULT '0' COMMENT 'Overrides the bot restriction to the inventory and room.',
  `acc_unlimited_pets` enum('0','1','2') NOT NULL DEFAULT '0' COMMENT 'Overrides the pet restriction to the inventory and room.',
  `acc_hide_ip` enum('0','1') NOT NULL DEFAULT '0',
  `acc_hide_mail` enum('0','1') NOT NULL DEFAULT '0',
  `acc_not_mimiced` enum('0','1') NOT NULL DEFAULT '0',
  `acc_chat_no_flood` enum('0','1') NOT NULL DEFAULT '0',
  `acc_staff_chat` enum('0','1') NOT NULL DEFAULT '0',
  `acc_staff_pick` enum('0','1') NOT NULL DEFAULT '0',
  `acc_enteranyroom` enum('0','1') NOT NULL DEFAULT '0',
  `acc_fullrooms` enum('0','1') NOT NULL DEFAULT '0',
  `acc_infinite_credits` enum('0','1') NOT NULL DEFAULT '0',
  `acc_infinite_pixels` enum('0','1') NOT NULL DEFAULT '0',
  `acc_infinite_points` enum('0','1') NOT NULL DEFAULT '0',
  `acc_ambassador` enum('0','1') NOT NULL DEFAULT '0',
  `acc_debug` enum('0','1') NOT NULL DEFAULT '0',
  `acc_chat_no_limit` enum('0','1') NOT NULL DEFAULT '0' COMMENT 'People with this permission node are always heard and can see all chat in the room regarding of maximum hearing distance in the room settings (In game)',
  `acc_chat_no_filter` enum('0','1') NOT NULL DEFAULT '0',
  `acc_nomute` enum('0','1') NOT NULL DEFAULT '0',
  `acc_guild_admin` enum('0','1') NOT NULL DEFAULT '0',
  `acc_catalog_ids` enum('0','1') NOT NULL DEFAULT '0',
  `acc_modtool_ticket_q` enum('0','1') NOT NULL DEFAULT '0',
  `acc_modtool_user_logs` enum('0','1') NOT NULL DEFAULT '0',
  `acc_modtool_user_alert` enum('0','1') NOT NULL DEFAULT '0',
  `acc_modtool_user_kick` enum('0','1') NOT NULL DEFAULT '0',
  `acc_modtool_user_ban` enum('0','1') NOT NULL DEFAULT '0',
  `acc_modtool_room_info` enum('0','1') NOT NULL DEFAULT '0',
  `acc_modtool_room_logs` enum('0','1') NOT NULL DEFAULT '0',
  `acc_trade_anywhere` enum('0','1') NOT NULL DEFAULT '0',
  `acc_update_notifications` enum('0','1') NOT NULL DEFAULT '0',
  `acc_helper_use_guide_tool` enum('0','1') NOT NULL DEFAULT '0',
  `acc_helper_give_guide_tours` enum('0','1') NOT NULL DEFAULT '0',
  `acc_helper_judge_chat_reviews` enum('0','1') NOT NULL DEFAULT '0',
  `acc_floorplan_editor` enum('0','1') NOT NULL DEFAULT '0',
  `acc_camera` enum('0','1') NOT NULL DEFAULT '0',
  `acc_ads_background` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_wordquiz` enum('0','1','2') NOT NULL DEFAULT '0',
  `acc_room_staff_tags` enum('0','1') NOT NULL DEFAULT '0',
  `acc_infinite_friends` enum('0','1') NOT NULL DEFAULT '0',
  `acc_unignorable` enum('0','1') NOT NULL DEFAULT '0',
  `acc_mimic_unredeemed` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_youtube_playlists` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_add_youtube_playlist` enum('0','1') NOT NULL DEFAULT '0',
  `auto_credits_amount` int(11) DEFAULT 0,
  `auto_pixels_amount` int(11) DEFAULT 0,
  `auto_gotw_amount` int(11) DEFAULT 0,
  `auto_points_amount` int(11) DEFAULT 0,
  `acc_mention` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_setstate` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_buildheight` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_setrotation` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_sellroom` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_buyroom` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_pay` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_kill` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_hoverboard` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_kiss` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_hug` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_welcome` enum('0','1','2') NOT NULL DEFAULT '0',
  `cmd_disable_effects` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_brb` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_nuke` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_slime` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_explain` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_closedice` enum('0','1','2') NOT NULL DEFAULT '1',
  `acc_closedice_room` enum('0','1','2') NOT NULL DEFAULT '2',
  `cmd_set` enum('0','1','2') NOT NULL DEFAULT '1',
  `cmd_furnidata` enum('0','1') NOT NULL DEFAULT '0',
  `kiss_cmd` enum('0','1','2') NOT NULL DEFAULT '0',
  `acc_calendar_force` enum('0','1') DEFAULT '0',
  `cmd_update_calendar` enum('0','1') NOT NULL DEFAULT '0',
  `cmd_update_chat_bubbles` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
INSERT INTO `permissions` VALUES
(1,'Member','',1,0,'0','','','1','0','1','0','0','0','1','0','0','0','0','1','0','0','1','0','0','0','1','0','0','1','1','1','1','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0',0,0,0,0,'0','1','1','1','1','1','1','1','1','1','1','0','2','1','1','1','1','1','2','1','0','0','0','0','0'),
(2,'VIP','',2,0,'0','','','1','0','1','0','0','0','1','0','0','0','0','1','0','0','0','0','0','0','1','0','0','1','1','1','1','1','0','0','0','0','0','1','0','0','0','0','0','0','0','0','1','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','0','0','0','0','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0',0,0,0,0,'0','1','1','1','1','1','1','1','1','1','1','0','2','1','1','1','1','1','2','1','0','0','0','0','0'),
(3,'X','',3,0,'0','','','1','0','1','0','0','0','1','0','0','0','0','1','0','0','0','0','0','0','1','0','0','1','1','1','1','1','0','0','0','0','0','1','0','0','0','0','0','0','0','0','1','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','0','0','0','0','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','1','1','1','1','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0',0,0,0,0,'0','1','1','1','1','1','1','1','1','1','1','0','2','1','1','1','1','1','2','1','0','0','0','0','0'),
(4,'Support','',4,0,'0','','','1','0','1','0','0','0','1','0','0','0','0','1','0','0','0','0','0','0','1','0','0','1','1','1','1','1','0','0','0','0','0','1','0','0','0','0','0','0','0','0','1','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','0','0','0','0','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','1','1','1','1','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0',0,0,0,0,'0','1','1','1','1','1','1','1','1','1','1','0','2','1','1','1','1','1','2','1','0','0','0','0','0'),
(5,'Moderator','',5,0,'0','','','1','0','1','0','0','0','1','0','0','0','0','1','0','0','0','0','0','0','1','0','0','1','1','1','1','1','0','0','0','0','0','1','0','0','0','0','0','0','0','0','1','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','0','0','0','0','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','1','1','1','1','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0',0,0,0,0,'0','1','1','1','1','1','1','1','1','1','1','0','2','1','1','1','1','1','2','1','0','0','0','0','0'),
(6,'Super Mod','',6,0,'0','','','1','0','1','0','0','0','1','0','0','0','0','1','0','0','0','0','0','0','1','0','0','1','1','1','1','1','0','0','0','0','0','1','0','0','0','0','0','0','0','0','1','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','1','0','0','0','0','0','2','0','0','0','0','0','0','0','0','0','0','0','0','0','1','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0','0',0,0,0,0,'0','1','1','1','1','1','1','1','1','1','1','0','2','1','1','1','1','1','2','1','0','0','0','0','0'),
(7,'Administrator','ADM',7,0,'0','ADM','#A1A1A1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','0','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','0','1','0','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','0','1','1','1','1','1','1','1','1','1','0','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','0','1','1','1',0,0,0,0,'1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','1','0','0','0','0');
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_actions` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `pet_type` int(11) NOT NULL,
  `pet_name` varchar(32) NOT NULL,
  `offspring_type` int(11) NOT NULL DEFAULT -1,
  `happy_actions` varchar(100) NOT NULL DEFAULT '',
  `tired_actions` varchar(100) NOT NULL DEFAULT '',
  `random_actions` varchar(100) NOT NULL DEFAULT '',
  `can_swim` enum('1','0') DEFAULT '0',
  PRIMARY KEY (`ID`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=77 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_breeding` (
  `pet_id` int(11) NOT NULL,
  `offspring_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_breeding_races` (
  `pet_type` int(11) NOT NULL,
  `rarity_level` int(11) NOT NULL,
  `breed` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_breeds` (
  `race` int(11) NOT NULL,
  `color_one` int(11) NOT NULL,
  `color_two` int(11) NOT NULL,
  `has_color_one` enum('0','1') NOT NULL DEFAULT '0',
  `has_color_two` enum('0','1') NOT NULL DEFAULT '0',
  UNIQUE KEY `idx_name` (`race`,`color_one`,`color_two`,`has_color_one`,`has_color_two`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_commands` (
  `pet_id` int(11) NOT NULL,
  `command_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_commands_data` (
  `command_id` int(11) NOT NULL,
  `text` varchar(15) NOT NULL,
  `required_level` int(11) NOT NULL,
  `reward_xp` int(11) NOT NULL DEFAULT 5,
  `cost_happiness` int(11) NOT NULL DEFAULT 0,
  `cost_energy` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`command_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_drinks` (
  `pet_id` int(11) NOT NULL DEFAULT 0 COMMENT 'Leave 0 to have it affect all pet types.',
  `item_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_foods` (
  `pet_id` int(11) NOT NULL DEFAULT 0 COMMENT 'Leave 0 to have it affect all pet types.',
  `item_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_items` (
  `pet_id` int(11) NOT NULL COMMENT 'Leave 0 to have it affect all pet types.',
  `item_id` int(11) NOT NULL COMMENT 'Item id of a item having one of the following interactions: nest, pet_food, pet_drink'
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `pet_vocals` (
  `pet_id` int(11) NOT NULL DEFAULT 0 COMMENT 'Leave 0 to have it apply to all pet types.',
  `type` enum('DISOBEY','DRINKING','EATING','GENERIC_HAPPY','GENERIC_NEUTRAL','GENERIC_SAD','GREET_OWNER','HUNGRY','LEVEL_UP','MUTED','PLAYFUL','SLEEPING','THIRSTY','TIRED','UNKNOWN_COMMAND') NOT NULL DEFAULT 'GENERIC_HAPPY',
  `message` varchar(100) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `polls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL DEFAULT 'Hey! We''d appreciate it if you could take some time to answer these questions. It will help improve our hotel.',
  `thanks_message` varchar(255) NOT NULL,
  `reward_badge` varchar(10) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `polls_answers` (
  `poll_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `question_id` int(11) NOT NULL,
  `answer` varchar(255) NOT NULL,
  UNIQUE KEY `unique_index` (`poll_id`,`user_id`,`question_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `polls_questions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) NOT NULL DEFAULT 0,
  `poll_id` int(11) NOT NULL,
  `order` int(11) NOT NULL,
  `question` varchar(255) NOT NULL,
  `type` int(11) NOT NULL DEFAULT 2,
  `min_selections` int(11) NOT NULL DEFAULT 1,
  `options` varchar(255) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `recycler_prizes` (
  `rarity` int(11) NOT NULL,
  `item_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_bans` (
  `room_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `ends` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_enter_log` (
  `room_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL,
  `exit_timestamp` int(11) NOT NULL DEFAULT 0,
  KEY `room_enter_log_room_id` (`room_id`) USING BTREE,
  KEY `room_enter_log_user_entry` (`user_id`,`timestamp`) USING BTREE,
  KEY `room_id` (`room_id`) USING BTREE,
  KEY `exit_timestamp` (`exit_timestamp`) USING BTREE,
  KEY `timestamps` (`timestamp`,`exit_timestamp`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_game_scores` (
  `room_id` int(11) NOT NULL,
  `game_start_timestamp` int(11) NOT NULL,
  `game_name` varchar(64) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL,
  `team_id` int(11) NOT NULL,
  `score` int(11) NOT NULL,
  `team_score` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_models` (
  `name` varchar(100) NOT NULL,
  `door_x` int(11) NOT NULL,
  `door_y` int(11) NOT NULL,
  `door_dir` int(11) NOT NULL DEFAULT 2,
  `heightmap` text NOT NULL,
  `public_items` text NOT NULL,
  `club_only` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`name`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_models_custom` (
  `id` int(11) NOT NULL,
  `name` varchar(100) NOT NULL,
  `door_x` int(11) NOT NULL,
  `door_y` int(11) NOT NULL,
  `door_dir` int(11) NOT NULL,
  `heightmap` text NOT NULL,
  UNIQUE KEY `id` (`id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_mutes` (
  `room_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `ends` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_promotions` (
  `room_id` int(11) NOT NULL,
  `title` varchar(127) NOT NULL,
  `description` varchar(1024) NOT NULL,
  `end_timestamp` int(11) NOT NULL DEFAULT 0,
  `start_timestamp` int(11) NOT NULL DEFAULT -1,
  `category` int(11) NOT NULL DEFAULT 0,
  UNIQUE KEY `room_id` (`room_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_rights` (
  `room_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_trade_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_one_id` int(11) NOT NULL,
  `user_two_id` int(11) NOT NULL,
  `user_one_ip` varchar(45) NOT NULL,
  `user_two_ip` varchar(45) NOT NULL,
  `timestamp` int(11) NOT NULL,
  `user_one_item_count` int(11) NOT NULL,
  `user_two_item_count` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `user_one_id` (`user_one_id`) USING BTREE,
  KEY `user_two_id` (`user_two_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_trade_log_items` (
  `id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  UNIQUE KEY `id` (`id`,`item_id`,`user_id`) USING BTREE,
  KEY `id_2` (`id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `id_3` (`id`,`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_trax` (
  `room_id` int(11) NOT NULL,
  `trax_item_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=armscii8 COLLATE=armscii8_general_ci ROW_FORMAT=COMPACT;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_trax_playlist` (
  `room_id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  KEY `room_id` (`room_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_votes` (
  `user_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `room_wordfilter` (
  `room_id` int(11) NOT NULL,
  `word` varchar(25) NOT NULL,
  UNIQUE KEY `unique_index` (`room_id`,`word`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `rooms` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `owner_id` int(11) NOT NULL DEFAULT 0,
  `owner_name` varchar(25) NOT NULL DEFAULT '',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  `description` varchar(512) NOT NULL DEFAULT '',
  `model` varchar(20) NOT NULL DEFAULT 'model_a',
  `password` varchar(20) NOT NULL DEFAULT '',
  `state` enum('open','locked','password','invisible') NOT NULL DEFAULT 'open',
  `users` int(11) NOT NULL DEFAULT 0,
  `users_max` int(11) NOT NULL DEFAULT 25,
  `guild_id` int(11) NOT NULL DEFAULT 0,
  `category` int(11) NOT NULL DEFAULT 1,
  `score` int(11) NOT NULL DEFAULT 0,
  `paper_floor` varchar(5) NOT NULL DEFAULT '0.0',
  `paper_wall` varchar(5) NOT NULL DEFAULT '0.0',
  `paper_landscape` varchar(5) NOT NULL DEFAULT '0.0',
  `thickness_wall` int(11) NOT NULL DEFAULT 0,
  `wall_height` int(11) NOT NULL DEFAULT -1,
  `thickness_floor` int(11) NOT NULL DEFAULT 0,
  `moodlight_data` varchar(254) NOT NULL DEFAULT '2,1,1,#000000,255;2,3,1,#000000,255;2,3,1,#000000,255;',
  `tags` varchar(500) NOT NULL DEFAULT '',
  `is_public` enum('0','1') NOT NULL DEFAULT '0',
  `is_staff_picked` enum('0','1') NOT NULL DEFAULT '0',
  `allow_other_pets` enum('0','1') NOT NULL DEFAULT '0',
  `allow_other_pets_eat` enum('0','1') NOT NULL DEFAULT '0',
  `allow_walkthrough` enum('0','1') NOT NULL DEFAULT '1',
  `allow_hidewall` enum('0','1') NOT NULL DEFAULT '0',
  `chat_mode` int(11) NOT NULL DEFAULT 0,
  `chat_weight` int(11) NOT NULL DEFAULT 1,
  `chat_speed` int(11) NOT NULL DEFAULT 1,
  `chat_hearing_distance` int(11) NOT NULL DEFAULT 50,
  `chat_protection` int(11) NOT NULL DEFAULT 2,
  `override_model` enum('0','1') NOT NULL DEFAULT '0',
  `who_can_mute` int(11) NOT NULL DEFAULT 0,
  `who_can_kick` int(11) NOT NULL DEFAULT 0,
  `who_can_ban` int(11) NOT NULL DEFAULT 0,
  `poll_id` int(11) NOT NULL DEFAULT 0,
  `roller_speed` int(11) NOT NULL DEFAULT 4,
  `promoted` enum('0','1') NOT NULL DEFAULT '0',
  `trade_mode` int(11) NOT NULL DEFAULT 2,
  `move_diagonally` enum('0','1') NOT NULL DEFAULT '1',
  `jukebox_active` enum('0','1') NOT NULL DEFAULT '0',
  `hidewired` enum('0','1') NOT NULL DEFAULT '0',
  `is_forsale` enum('0','1','2') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `name` (`name`) USING BTREE,
  KEY `owner_name` (`owner_name`) USING BTREE,
  KEY `owner_id` (`owner_id`) USING BTREE,
  KEY `guild_id` (`guild_id`) USING BTREE,
  KEY `category` (`category`) USING BTREE,
  KEY `public_status` (`is_public`,`is_staff_picked`) USING BTREE,
  KEY `togehter_data` (`name`,`owner_name`,`guild_id`) USING BTREE,
  KEY `tags` (`tags`) USING BTREE,
  KEY `state` (`state`) USING BTREE,
  KEY `description` (`description`) USING BTREE,
  KEY `users` (`users`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `sanction_levels` (
  `level` int(11) NOT NULL,
  `type` enum('ALERT','BAN','MUTE') NOT NULL,
  `hour_length` int(11) NOT NULL,
  `probation_days` int(11) NOT NULL,
  PRIMARY KEY (`level`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `sanctions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `habbo_id` int(11) NOT NULL DEFAULT 0,
  `sanction_level` int(11) NOT NULL DEFAULT 0,
  `probation_timestamp` int(11) NOT NULL DEFAULT 0,
  `reason` varchar(255) NOT NULL DEFAULT '',
  `trade_locked_until` int(11) NOT NULL DEFAULT 0,
  `is_muted` tinyint(1) NOT NULL DEFAULT 0,
  `mute_duration` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `soundtracks` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `author` varchar(50) NOT NULL,
  `track` text NOT NULL,
  `length` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `special_enables` (
  `effect_id` int(11) NOT NULL,
  `min_rank` int(11) NOT NULL,
  UNIQUE KEY `effect_id` (`effect_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `support_cfh_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name_internal` varchar(255) DEFAULT NULL,
  `name_external` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `support_cfh_topics` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `category_id` int(11) DEFAULT NULL,
  `name_internal` varchar(255) DEFAULT NULL,
  `name_external` varchar(255) DEFAULT NULL,
  `action` enum('mods','auto_ignore','auto_reply') DEFAULT 'mods',
  `ignore_target` enum('0','1') NOT NULL DEFAULT '0',
  `auto_reply` text DEFAULT NULL,
  `default_sanction` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `support_issue_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT 'PII',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=3 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `support_issue_presets` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `category` int(11) NOT NULL DEFAULT 1,
  `name` varchar(100) NOT NULL DEFAULT '',
  `message` varchar(300) NOT NULL DEFAULT '',
  `reminder` varchar(100) NOT NULL,
  `ban_for` int(11) NOT NULL DEFAULT 0 COMMENT '100000 = perm ban',
  `mute_for` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `support_presets` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` enum('user','room') NOT NULL DEFAULT 'user',
  `preset` varchar(200) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `support_tickets` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `state` int(11) NOT NULL DEFAULT 0,
  `type` int(11) NOT NULL DEFAULT 1,
  `timestamp` int(11) NOT NULL DEFAULT 0,
  `score` int(11) NOT NULL DEFAULT 0,
  `sender_id` int(11) NOT NULL DEFAULT 0,
  `reported_id` int(11) NOT NULL DEFAULT 0,
  `room_id` int(11) NOT NULL DEFAULT 0,
  `mod_id` int(11) NOT NULL DEFAULT 0,
  `issue` varchar(500) NOT NULL DEFAULT '',
  `category` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL,
  `thread_id` int(11) NOT NULL,
  `comment_id` int(11) NOT NULL,
  `photo_item_id` int(11) NOT NULL DEFAULT -1,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `id` (`id`) USING BTREE,
  KEY `state` (`state`) USING BTREE,
  KEY `type` (`type`) USING BTREE,
  KEY `timestamp` (`timestamp`) USING BTREE,
  KEY `user_data` (`sender_id`,`reported_id`) USING BTREE,
  KEY `room_id` (`room_id`) USING BTREE,
  KEY `issue` (`issue`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `trax_playlist` (
  `trax_item_id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=armscii8 COLLATE=armscii8_general_ci ROW_FORMAT=COMPACT;
COMMIT;
CREATE TABLE IF NOT EXISTS `user_window_settings` (
  `user_id` int(11) NOT NULL,
  `x` int(11) NOT NULL DEFAULT 100,
  `y` int(11) NOT NULL DEFAULT 100,
  `width` int(11) NOT NULL DEFAULT 435,
  `height` int(11) NOT NULL DEFAULT 535,
  `open_searches` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(25) NOT NULL,
  `real_name` varchar(25) NOT NULL DEFAULT 'KREWS DEV',
  `password` varchar(64) NOT NULL,
  `mail` varchar(500) DEFAULT NULL,
  `mail_verified` enum('0','1') NOT NULL DEFAULT '0',
  `account_created` int(11) NOT NULL,
  `account_day_of_birth` int(11) NOT NULL DEFAULT 0,
  `last_login` int(11) NOT NULL DEFAULT 0,
  `last_online` int(11) NOT NULL DEFAULT 0,
  `motto` varchar(127) NOT NULL DEFAULT '',
  `look` varchar(256) NOT NULL DEFAULT 'hr-115-42.hd-195-19.ch-3030-82.lg-275-1408.fa-1201.ca-1804-64',
  `gender` enum('M','F') NOT NULL DEFAULT 'M',
  `rank` int(11) NOT NULL DEFAULT 1,
  `credits` int(11) NOT NULL DEFAULT 2500,
  `pixels` int(11) NOT NULL DEFAULT 500,
  `points` int(11) NOT NULL DEFAULT 10,
  `online` enum('0','1','2') NOT NULL DEFAULT '0',
  `auth_ticket` varchar(256) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
  `ip_register` varchar(45) NOT NULL,
  `ip_current` varchar(45) NOT NULL COMMENT 'Have your CMS update this IP. If you do not do this IP banning won''t work!',
  `machine_id` varchar(64) NOT NULL DEFAULT '',
  `home_room` int(11) NOT NULL DEFAULT 0,
  `secret_key` varchar(40) DEFAULT NULL,
  `pincode` varchar(11) DEFAULT NULL,
  `extra_rank` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `username` (`username`) USING BTREE,
  UNIQUE KEY `id` (`id`) USING BTREE,
  UNIQUE KEY `id_2` (`id`) USING BTREE,
  UNIQUE KEY `id_3` (`id`) USING BTREE,
  KEY `account_created` (`account_created`) USING BTREE,
  KEY `last_login` (`last_login`) USING BTREE,
  KEY `last_online` (`last_online`) USING BTREE,
  KEY `figure` (`motto`,`look`,`gender`) USING BTREE,
  KEY `auth_ticket` (`auth_ticket`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_achievements` (
  `user_id` int(11) NOT NULL,
  `achievement_name` varchar(255) NOT NULL,
  `progress` int(11) NOT NULL DEFAULT 1,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `achievement_name` (`achievement_name`) USING BTREE,
  KEY `progress` (`progress`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_achievements_queue` (
  `user_id` int(11) NOT NULL,
  `achievement_id` int(11) NOT NULL,
  `amount` int(11) NOT NULL,
  UNIQUE KEY `unique_index` (`user_id`,`achievement_id`) USING BTREE,
  UNIQUE KEY `data` (`user_id`,`achievement_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_badges` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `slot_id` int(11) NOT NULL DEFAULT 0,
  `badge_code` varchar(32) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_clothing` (
  `user_id` int(11) NOT NULL,
  `clothing_id` int(11) NOT NULL,
  UNIQUE KEY `user_id` (`user_id`,`clothing_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_currency` (
  `user_id` int(11) NOT NULL,
  `type` int(11) NOT NULL,
  `amount` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`,`type`) USING BTREE,
  UNIQUE KEY `userdata` (`user_id`,`type`) USING BTREE,
  KEY `amount` (`amount`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_effects` (
  `user_id` int(11) NOT NULL,
  `effect` int(11) NOT NULL,
  `duration` int(11) NOT NULL DEFAULT 86400,
  `activation_timestamp` int(11) NOT NULL DEFAULT -1,
  `total` int(11) NOT NULL DEFAULT 1,
  UNIQUE KEY `user_id` (`user_id`,`effect`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_favorite_rooms` (
  `user_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL,
  UNIQUE KEY `user_id` (`user_id`,`room_id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_ignored` (
  `user_id` int(11) NOT NULL,
  `target_id` int(11) NOT NULL,
  KEY `user_id` (`user_id`,`target_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_navigator_settings` (
  `user_id` int(11) NOT NULL,
  `caption` varchar(128) NOT NULL,
  `list_type` enum('list','thumbnails') NOT NULL DEFAULT 'list',
  `display` enum('visible','collapsed') NOT NULL DEFAULT 'visible',
  UNIQUE KEY `userid` (`user_id`) USING BTREE,
  KEY `list_type` (`list_type`) USING BTREE,
  KEY `display` (`display`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_pets` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL,
  `name` varchar(15) NOT NULL DEFAULT 'User Pet',
  `race` int(11) NOT NULL,
  `type` int(11) NOT NULL,
  `color` varchar(6) NOT NULL,
  `happiness` int(11) NOT NULL DEFAULT 100,
  `experience` int(11) NOT NULL DEFAULT 0,
  `energy` int(11) NOT NULL DEFAULT 100,
  `hunger` int(11) NOT NULL DEFAULT 0,
  `thirst` int(11) NOT NULL DEFAULT 0,
  `respect` int(11) NOT NULL DEFAULT 0,
  `created` int(11) NOT NULL,
  `x` int(11) NOT NULL DEFAULT 0,
  `y` int(11) NOT NULL DEFAULT 0,
  `z` double NOT NULL DEFAULT 0,
  `rot` int(11) NOT NULL DEFAULT 0,
  `hair_style` int(11) NOT NULL DEFAULT -1,
  `hair_color` int(11) NOT NULL DEFAULT 0,
  `saddle` enum('0','1') NOT NULL DEFAULT '0',
  `ride` enum('0','1') NOT NULL DEFAULT '0',
  `mp_type` int(11) NOT NULL DEFAULT 0,
  `mp_color` int(11) NOT NULL DEFAULT 0,
  `mp_nose` int(11) NOT NULL DEFAULT 0,
  `mp_nose_color` tinyint(4) NOT NULL DEFAULT 0,
  `mp_eyes` int(11) NOT NULL DEFAULT 0,
  `mp_eyes_color` tinyint(4) NOT NULL DEFAULT 0,
  `mp_mouth` int(11) NOT NULL DEFAULT 0,
  `mp_mouth_color` tinyint(4) NOT NULL DEFAULT 0,
  `mp_death_timestamp` int(11) NOT NULL DEFAULT 0,
  `mp_breedable` enum('0','1') NOT NULL DEFAULT '0',
  `mp_allow_breed` enum('0','1') NOT NULL DEFAULT '0',
  `gnome_data` varchar(80) NOT NULL DEFAULT '',
  `mp_is_dead` tinyint(1) NOT NULL DEFAULT 0,
  `saddle_item_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_recipes` (
  `user_id` int(11) NOT NULL,
  `recipe` int(11) NOT NULL,
  UNIQUE KEY `user_id` (`user_id`,`recipe`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_saved_searches` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `search_code` varchar(255) NOT NULL,
  `filter` varchar(255) DEFAULT NULL,
  `user_id` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0 COMMENT 'WARNING: DONT HAVE YOUR CMS INSERT ANYTHING IN HERE. THE EMULATOR DOES THIS FOR YOU!',
  `credits` int(11) NOT NULL DEFAULT 0,
  `achievement_score` int(11) NOT NULL DEFAULT 0,
  `daily_respect_points` int(11) NOT NULL DEFAULT 3,
  `daily_pet_respect_points` int(11) NOT NULL DEFAULT 3,
  `respects_given` int(11) NOT NULL DEFAULT 0,
  `respects_received` int(11) NOT NULL DEFAULT 0,
  `guild_id` int(11) NOT NULL DEFAULT 0,
  `can_change_name` enum('0','1') CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0',
  `can_trade` enum('0','1') NOT NULL DEFAULT '1',
  `is_citizen` enum('0','1') NOT NULL DEFAULT '0',
  `citizen_level` int(11) NOT NULL DEFAULT 0,
  `helper_level` int(11) NOT NULL DEFAULT 0,
  `tradelock_amount` int(11) NOT NULL DEFAULT 0,
  `cfh_send` int(11) NOT NULL DEFAULT 0 COMMENT 'Amount of CFHs been send. Not include abusive.',
  `cfh_abusive` int(11) NOT NULL DEFAULT 0 COMMENT 'Amount of abusive CFHs have been send.',
  `cfh_warnings` int(11) NOT NULL DEFAULT 0 COMMENT 'Amount of warnings a user has received.',
  `cfh_bans` int(11) NOT NULL DEFAULT 0 COMMENT 'Amount of bans a user has received.',
  `block_following` enum('0','1') CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0',
  `block_friendrequests` enum('0','1') CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0',
  `block_roominvites` enum('0','1') NOT NULL DEFAULT '0',
  `volume_system` int(11) NOT NULL DEFAULT 100,
  `volume_furni` int(11) NOT NULL DEFAULT 100,
  `volume_trax` int(11) NOT NULL DEFAULT 100,
  `old_chat` enum('0','1') NOT NULL DEFAULT '0',
  `block_camera_follow` enum('0','1') NOT NULL DEFAULT '0',
  `chat_color` int(11) NOT NULL DEFAULT 0,
  `home_room` int(11) NOT NULL DEFAULT 0,
  `online_time` int(11) NOT NULL DEFAULT 0 COMMENT 'Total online time in seconds.',
  `tags` varchar(255) NOT NULL DEFAULT 'Arcturus Emulator;',
  `club_expire_timestamp` int(11) NOT NULL DEFAULT 0,
  `login_streak` int(11) NOT NULL DEFAULT 0,
  `rent_space_id` int(11) NOT NULL DEFAULT 0,
  `rent_space_endtime` int(11) NOT NULL DEFAULT 0,
  `hof_points` int(11) NOT NULL DEFAULT 0,
  `block_alerts` enum('0','1') NOT NULL DEFAULT '0',
  `talent_track_citizenship_level` int(11) NOT NULL DEFAULT -1,
  `talent_track_helpers_level` int(11) NOT NULL DEFAULT -1,
  `ignore_bots` enum('0','1') NOT NULL DEFAULT '0',
  `ignore_pets` enum('0','1') NOT NULL DEFAULT '0',
  `nux` enum('0','1') NOT NULL DEFAULT '0',
  `mute_end_timestamp` int(11) NOT NULL DEFAULT 0,
  `allow_name_change` enum('0','1') NOT NULL DEFAULT '0',
  `perk_trade` enum('0','1') NOT NULL DEFAULT '0' COMMENT 'Defines if a player has obtained the perk TRADE. When hotel.trading.requires.perk is set to 1, this perk is required in order to trade. Perk is obtained from the talen track.',
  `forums_post_count` int(11) DEFAULT 0,
  `ui_flags` int(11) NOT NULL DEFAULT 1,
  `has_gotten_default_saved_searches` tinyint(1) NOT NULL DEFAULT 0,
  `hc_gifts_claimed` int(11) DEFAULT 0,
  `last_hc_payday` int(11) DEFAULT 0,
  `max_rooms` int(11) DEFAULT 50,
  `max_friends` int(11) DEFAULT 300,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `user_id` (`user_id`) USING BTREE,
  KEY `achievement_score` (`achievement_score`) USING BTREE,
  KEY `guild_id` (`guild_id`) USING BTREE,
  KEY `can_trade` (`can_trade`) USING BTREE,
  KEY `online_time` (`online_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_subscriptions` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(10) unsigned DEFAULT NULL,
  `subscription_type` varchar(255) DEFAULT NULL,
  `timestamp_start` int(10) unsigned DEFAULT NULL,
  `duration` int(10) unsigned DEFAULT NULL,
  `active` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `subscription_type` (`subscription_type`) USING BTREE,
  KEY `timestamp_start` (`timestamp_start`) USING BTREE,
  KEY `active` (`active`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_target_offer_purchases` (
  `user_id` int(11) NOT NULL,
  `offer_id` int(11) NOT NULL,
  `state` int(11) NOT NULL DEFAULT 0,
  `amount` int(11) NOT NULL DEFAULT 0,
  `last_purchase` int(11) NOT NULL DEFAULT 0,
  UNIQUE KEY `use_id` (`user_id`,`offer_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `users_wardrobe` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `slot_id` int(11) NOT NULL DEFAULT 0,
  `look` varchar(256) NOT NULL,
  `gender` enum('M','F') NOT NULL DEFAULT 'F',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `voucher_history` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `voucher_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `vouchers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(10) NOT NULL,
  `credits` int(11) NOT NULL DEFAULT 0,
  `points` int(11) NOT NULL DEFAULT 0,
  `points_type` int(11) NOT NULL DEFAULT 0,
  `catalog_item_id` int(11) NOT NULL DEFAULT 0,
  `amount` int(11) NOT NULL DEFAULT 1,
  `limit` int(11) NOT NULL DEFAULT -1,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `wired_rewards_given` (
  `wired_item` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `reward_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
CREATE TABLE IF NOT EXISTS `wordfilter` (
  `key` varchar(256) NOT NULL COMMENT 'The word to filter.',
  `replacement` varchar(16) NOT NULL COMMENT 'What the word should be replaced with.',
  `hide` enum('0','1') NOT NULL DEFAULT '0' COMMENT 'Wether the whole message that contains this word should be hidden from being displayed.',
  `report` enum('0','1') NOT NULL DEFAULT '0' COMMENT 'Wether the message should be reported as auto-report to the moderators.',
  `mute` int(11) NOT NULL DEFAULT 0 COMMENT 'Time user gets muted for mentioning this word.',
  UNIQUE KEY `key` (`key`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
COMMIT;
CREATE TABLE IF NOT EXISTS `youtube_playlists` (
  `item_id` int(11) NOT NULL,
  `playlist_id` varchar(255) NOT NULL COMMENT 'YouTube playlist ID',
  `order` int(11) NOT NULL,
  UNIQUE KEY `item_id` (`item_id`,`playlist_id`,`order`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=FIXED;
COMMIT;
SET FOREIGN_KEY_CHECKS=1;
