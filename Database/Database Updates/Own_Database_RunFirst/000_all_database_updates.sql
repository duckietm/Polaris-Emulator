SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET @OLD_SQL_MODE = @@SQL_MODE;
SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';


-- =============================================================================
-- From: UpdateDatabase_Allow_diagonale.sql
-- =============================================================================
INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('pathfinder.diagonal.enabled', '1');


-- =============================================================================
-- From: UpdateDatabase_BOT.sql
-- =============================================================================
INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('hotel.bot.limit.walking.distance', '1');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('hotel.bot.limit.walking.distance.radius', '5');


-- =============================================================================
-- From: UpdateDatabase_Banners.sql
-- =============================================================================
ALTER TABLE `users`
  ADD COLUMN IF NOT EXISTS `background_id` INT(11) NOT NULL DEFAULT 0 AFTER `machine_id`,
  ADD COLUMN IF NOT EXISTS `background_stand_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_id`,
  ADD COLUMN IF NOT EXISTS `background_overlay_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_stand_id`;


-- =============================================================================
-- From: UpdateDatabase_DanceCMD.sql
-- =============================================================================
ALTER TABLE `permissions`
  ADD COLUMN IF NOT EXISTS `cms_dance` ENUM('0','1') NULL DEFAULT '0' AFTER `cmd_credits`;

INSERT IGNORE INTO `emulator_texts` (`key`, `value`)
VALUES ('commands.description.cmd_dance', 'dance around the world ! use 1 t/m 4 and 0 to stop');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`)
VALUES ('commands.keys.cmd_dance', 'dance');


-- =============================================================================
-- From: UpdateDatabase_Happiness.sql
-- =============================================================================
-- Rename key if the old one exists
UPDATE `emulator_texts`
SET `key` = 'generic.pet.happiness', `value` = 'Happiness'
WHERE `key` = 'generic.pet.happyness';

-- Rename columns (IF EXISTS prevents error if already renamed)
ALTER TABLE `pet_commands_data`
  CHANGE COLUMN IF EXISTS `cost_happyness` `cost_happiness` int(11) NOT NULL DEFAULT '0';

ALTER TABLE `users_pets`
  CHANGE COLUMN IF EXISTS `happyness` `happiness` int(11) NOT NULL DEFAULT '100';


-- =============================================================================
-- From: UpdateDatabase_Websocket.sql
-- =============================================================================
INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('websockets.whitelist', 'localhost');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('ws.nitro.host', '0.0.0.0');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('ws.nitro.ip.header', '');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('ws.nitro.port', '2096');


-- =============================================================================
-- From: UpdateDatabase_unignorable.sql
-- =============================================================================
ALTER TABLE `permissions`
  ADD COLUMN IF NOT EXISTS `acc_unignorable` ENUM('0','1') NOT NULL DEFAULT '0';


-- =============================================================================
-- From: Default_Camera.sql
-- =============================================================================
CREATE TABLE IF NOT EXISTS `camera_web` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `user_id` INT(11) NOT NULL,
  `room_id` INT(11) NOT NULL DEFAULT 0,
  `timestamp` INT(11) NOT NULL DEFAULT 0,
  `url` VARCHAR(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  INDEX `idx_camera_web_user_id` (`user_id`),
  INDEX `idx_camera_web_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Camera emulator settings
INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('camera.url', 'http://localhost/camera/');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('imager.location.output.camera', '/path/to/www/camera/');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('imager.location.output.thumbnail', '/path/to/www/thumbnails/');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('camera.item_id', '0');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('camera.price.credits', '2');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('camera.price.points', '0');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('camera.price.points.publish', '1');

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('camera.extradata', '{"t":"%timestamp%","u":"%id%","m":"","s":"%room_id%","w":"%url%"}');

INSERT INTO emulator_settings (`key`, `value`) VALUES 
('session.reconnect.grace.seconds', '5');

INSERT INTO emulator_settings (`key`, `value`) VALUES 
('session.reconnect.effect.id', '188');

-- Camera emulator texts
INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('camera.permission', 'You do not have permission to use the camera.');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('camera.wait', 'Please wait %seconds% more seconds before taking another photo.');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('camera.error.creation', 'An error occurred while processing your photo. Please try again.');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('camera.daily.limit', 'You have reached the daily photo limit. Try again tomorrow.');


-- =============================================================================
-- From: 07012026_UpdateDatabase_to_4-0-1.sql
-- =============================================================================

-- Wired abuse protection settings
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.abuse.max.recursion.depth', '10')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.abuse.max.events.per.window', '100')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.abuse.rate.limit.window.ms', '10000')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.abuse.ban.duration.ms', '600000')
ON DUPLICATE KEY UPDATE `key` = `key`;

-- Wired abuse texts
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
('wired.abuse.room.alert', 'Wired execution has been temporarily disabled in this room due to abuse detection. It will resume in %minutes% minutes.')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
('wired.abuse.staff.title', 'Wired Abuse Detected')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
('wired.abuse.staff.message', 'Room: %roomname%\nOwner: %owner%\nBanned for %minutes% minutes.')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
('wired.abuse.staff.link', 'Go to Room')
ON DUPLICATE KEY UPDATE `key` = `key`;

-- Wired tick resolution
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.tick.resolution', '100')
ON DUPLICATE KEY UPDATE `key` = `key`;

-- Wired engine configuration
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.engine.enabled', '1'),
('wired.engine.exclusive', '1'),
('wired.engine.maxStepsPerStack', '100'),
('wired.engine.debug', '0')
ON DUPLICATE KEY UPDATE `key` = `key`;

-- Wired tick system configuration
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.tick.interval.ms', '50'),
('wired.tick.debug', '0'),
('wired.tick.thread.priority', '6')
ON DUPLICATE KEY UPDATE `key` = `key`;

-- Pathfinder settings
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('pathfinder.click.delay', '0')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('pathfinder.retro-style.diagonals', '0')
ON DUPLICATE KEY UPDATE `key` = `key`;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('pathfinder.step.allow.falling', '1')
ON DUPLICATE KEY UPDATE `key` = `key`;


-- =============================================================================
-- From: 09012026_UpdateDatabase_to_4-0-2.sql + 12012026_Battle Banzai.sql
-- (These two files are identical - deduplicated here)
-- =============================================================================
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('hotel.banzai.fill.max_queue', '50'),
('hotel.banzai.fill.cooldown_ms', '100')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);


-- =============================================================================
-- From: 12012026_Breeding Fixes.sql
-- =============================================================================

-- Recreate pet_breeding with correct structure
CREATE TABLE IF NOT EXISTS `pet_breeding` (
  `pet_id` int(11) NOT NULL COMMENT 'Parent pet type',
  `offspring_id` int(11) NOT NULL COMMENT 'Baby pet type',
  PRIMARY KEY (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

TRUNCATE TABLE `pet_breeding`;

INSERT INTO `pet_breeding` (`pet_id`, `offspring_id`) VALUES
(0, 29),   -- Dog -> Baby Dog
(1, 28),   -- Cat -> Baby Cat
(3, 25),   -- Terrier -> Baby Terrier
(4, 24),   -- Bear -> Baby Bear
(5, 30);   -- Pig -> Baby Pig

-- Recreate pet_breeding_races with correct structure
CREATE TABLE IF NOT EXISTS `pet_breeding_races` (
  `pet_type` int(11) NOT NULL COMMENT 'Baby pet type (offspring)',
  `rarity_level` int(11) NOT NULL COMMENT '1=Common, 2=Uncommon, 3=Rare, 4=Epic',
  `breed` int(11) NOT NULL COMMENT 'Visual breed/color variant',
  PRIMARY KEY (`pet_type`, `rarity_level`, `breed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

TRUNCATE TABLE `pet_breeding_races`;

-- Baby Dog (29) - Offspring of Dog (0)
INSERT INTO `pet_breeding_races` (`pet_type`, `rarity_level`, `breed`) VALUES
(29, 1, 0), (29, 1, 1), (29, 1, 2), (29, 1, 3),
(29, 1, 4), (29, 1, 5), (29, 1, 6), (29, 1, 7),
(29, 2, 8), (29, 2, 9), (29, 2, 10), (29, 2, 11), (29, 2, 12),
(29, 3, 13), (29, 3, 14), (29, 3, 15), (29, 3, 16),
(29, 4, 17), (29, 4, 18), (29, 4, 19);

-- Baby Cat (28) - Offspring of Cat (1)
INSERT INTO `pet_breeding_races` (`pet_type`, `rarity_level`, `breed`) VALUES
(28, 1, 0), (28, 1, 1), (28, 1, 2), (28, 1, 3),
(28, 1, 4), (28, 1, 5), (28, 1, 6), (28, 1, 7),
(28, 2, 8), (28, 2, 9), (28, 2, 10), (28, 2, 11), (28, 2, 12),
(28, 3, 13), (28, 3, 14), (28, 3, 15), (28, 3, 16),
(28, 4, 17), (28, 4, 18), (28, 4, 19);

-- Baby Terrier (25) - Offspring of Terrier (3)
INSERT INTO `pet_breeding_races` (`pet_type`, `rarity_level`, `breed`) VALUES
(25, 1, 0), (25, 1, 1), (25, 1, 2), (25, 1, 3),
(25, 1, 4), (25, 1, 5), (25, 1, 6), (25, 1, 7),
(25, 2, 8), (25, 2, 9), (25, 2, 10), (25, 2, 11), (25, 2, 12),
(25, 3, 13), (25, 3, 14), (25, 3, 15), (25, 3, 16),
(25, 4, 17), (25, 4, 18), (25, 4, 19);

-- Baby Bear (24) - Offspring of Bear (4)
INSERT INTO `pet_breeding_races` (`pet_type`, `rarity_level`, `breed`) VALUES
(24, 1, 0), (24, 1, 1), (24, 1, 2), (24, 1, 3),
(24, 1, 4), (24, 1, 5), (24, 1, 6), (24, 1, 7),
(24, 2, 8), (24, 2, 9), (24, 2, 10), (24, 2, 11), (24, 2, 12),
(24, 3, 13), (24, 3, 14), (24, 3, 15), (24, 3, 16),
(24, 4, 17), (24, 4, 18), (24, 4, 19);

-- Baby Pig (30) - Offspring of Pig (5)
INSERT INTO `pet_breeding_races` (`pet_type`, `rarity_level`, `breed`) VALUES
(30, 1, 0), (30, 1, 1), (30, 1, 2), (30, 1, 3),
(30, 1, 4), (30, 1, 5), (30, 1, 6), (30, 1, 7),
(30, 2, 8), (30, 2, 9), (30, 2, 10), (30, 2, 11), (30, 2, 12),
(30, 3, 13), (30, 3, 14), (30, 3, 15), (30, 3, 16),
(30, 4, 17), (30, 4, 18), (30, 4, 19);

-- Fix pet_actions offspring_type values
UPDATE `pet_actions` SET `offspring_type` = 29 WHERE `pet_type` = 0;
UPDATE `pet_actions` SET `offspring_type` = 28 WHERE `pet_type` = 1;
UPDATE `pet_actions` SET `offspring_type` = 25 WHERE `pet_type` = 3;
UPDATE `pet_actions` SET `offspring_type` = 24 WHERE `pet_type` = 4;
UPDATE `pet_actions` SET `offspring_type` = 30 WHERE `pet_type` = 5;
UPDATE `pet_actions` SET `offspring_type` = -1 WHERE `pet_type` NOT IN (0, 1, 3, 4, 5);

-- Fix items_base whitespace in interaction_type
UPDATE `items_base` SET `interaction_type` = TRIM(`interaction_type`);

-- Ensure breeding nest items have correct interaction_type
UPDATE `items_base` SET `interaction_type` = 'breeding_nest'
WHERE `item_name` LIKE 'pet_breeding_%' AND `interaction_type` != 'breeding_nest';


-- =============================================================================
-- From: 12012026_ChatBubbles.sql
-- =============================================================================
ALTER TABLE `permissions`
  ADD COLUMN IF NOT EXISTS `cmd_update_chat_bubbles` ENUM('0','1') NOT NULL DEFAULT '0';

CREATE TABLE IF NOT EXISTS `chat_bubbles` (
  `type` INT(11) NOT NULL AUTO_INCREMENT COMMENT 'Only 46 and higher will work',
  `name` VARCHAR(255) NOT NULL DEFAULT '',
  `permission` VARCHAR(255) NOT NULL DEFAULT '',
  `overridable` TINYINT(1) NOT NULL DEFAULT 1,
  `triggers_talking_furniture` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('commands.keys.cmd_update_chat_bubbles', 'update_chat_bubbles');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('commands.success.cmd_update_chat_bubbles', 'Successfully updated chat bubbles');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('commands.description.cmd_update_chat_bubbles', ':update_chat_bubbles');


-- =============================================================================
-- From: 16032026_updateall_command.sql
-- =============================================================================
ALTER TABLE `permissions`
  ADD COLUMN IF NOT EXISTS `cmd_update_all` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `cmd_update_achievements`;

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('commands.keys.cmd_update_all', 'update_all');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('commands.description.cmd_update_all', ':update_all');

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
('commands.succes.cmd_update_all', 'Successfully updated everything!');


-- =============================================================================
-- From: 17032026_allow_underpass.sql
-- =============================================================================
ALTER TABLE `rooms`
  ADD COLUMN IF NOT EXISTS `allow_underpass` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `move_diagonally`;


-- =============================================================================
-- From: 19032026_hotel_timezone.sql
-- =============================================================================
INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
VALUES ('hotel.timezone', 'Europe/Rome');


-- =============================================================================
-- From: 06042026_builders_club_catalog_offers.sql
-- =============================================================================
ALTER TABLE `catalog_club_offers`
  MODIFY COLUMN `type` ENUM('HC','VIP','BUILDERS_CLUB','BUILDERS_CLUB_ADDON') NOT NULL DEFAULT 'HC';

ALTER TABLE `catalog_pages`
  MODIFY COLUMN `page_layout` ENUM(
    'default_3x3',
    'club_buy',
    'club_gift',
    'frontpage',
    'spaces',
    'recycler',
    'recycler_info',
    'recycler_prizes',
    'trophies',
    'plasto',
    'marketplace',
    'marketplace_own_items',
    'spaces_new',
    'soundmachine',
    'guilds',
    'guild_furni',
    'info_duckets',
    'info_rentables',
    'info_pets',
    'roomads',
    'single_bundle',
    'sold_ltd_items',
    'badge_display',
    'bots',
    'pets',
    'pets2',
    'pets3',
    'productpage1',
    'room_bundle',
    'recent_purchases',
    'default_3x3_color_grouping',
    'guild_forum',
    'vip_buy',
    'info_loyalty',
    'loyalty_vip_buy',
    'collectibles',
    'petcustomization',
    'frontpage_featured',
    'builders_club_frontpage',
    'builders_club_addons',
    'builders_club_loyalty'
  ) NOT NULL DEFAULT 'default_3x3';

ALTER TABLE `catalog_pages`
  ADD COLUMN `catalog_mode` ENUM('NORMAL','BUILDER','BOTH') NOT NULL DEFAULT 'NORMAL' AFTER `club_only`;

ALTER TABLE `catalog_pages_bc`
  MODIFY COLUMN `page_layout` ENUM(
    'default_3x3',
    'club_buy',
    'club_gift',
    'frontpage',
    'spaces',
    'recycler',
    'recycler_info',
    'recycler_prizes',
    'trophies',
    'plasto',
    'marketplace',
    'marketplace_own_items',
    'spaces_new',
    'soundmachine',
    'guilds',
    'guild_furni',
    'info_duckets',
    'info_rentables',
    'info_pets',
    'roomads',
    'single_bundle',
    'sold_ltd_items',
    'badge_display',
    'bots',
    'pets',
    'pets2',
    'pets3',
    'productpage1',
    'room_bundle',
    'recent_purchases',
    'default_3x3_color_grouping',
    'guild_forum',
    'vip_buy',
    'info_loyalty',
    'loyalty_vip_buy',
    'collectibles',
    'petcustomization',
    'frontpage_featured',
    'builders_club_frontpage',
    'builders_club_addons',
    'builders_club_loyalty'
  ) NOT NULL DEFAULT 'default_3x3';

ALTER TABLE `users_settings`
  ADD COLUMN IF NOT EXISTS `builders_club_bonus_furni` INT(11) NOT NULL DEFAULT 0 AFTER `hc_gifts_claimed`;


INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`)
VALUES ( 'acc_staff_chat', 1, 'Grants access to the in-game Staff Chat group buddy: receives broadcasts from other staff and can broadcast to anyone holding this permission.' )
ON DUPLICATE KEY UPDATE `max_value` = VALUES(`max_value`), `comment`   = VALUES(`comment`);

-- =============================================================================
-- Done.
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 1;
SET SQL_MODE = @OLD_SQL_MODE;
