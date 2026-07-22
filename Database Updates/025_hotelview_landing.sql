CREATE TABLE IF NOT EXISTS `hotelview_landing_settings` (
    `id` TINYINT UNSIGNED NOT NULL,
    `background_url` VARCHAR(512) NOT NULL DEFAULT '',
    `left_url` VARCHAR(512) NOT NULL DEFAULT '',
    `right_url` VARCHAR(512) NOT NULL DEFAULT '',
    `drape_url` VARCHAR(512) NOT NULL DEFAULT '',
    `left_x` INT NOT NULL DEFAULT -1,
    `left_y` INT NOT NULL DEFAULT -1,
    `right_x` INT NOT NULL DEFAULT -1,
    `right_y` INT NOT NULL DEFAULT -1,
    `drape_x` INT NOT NULL DEFAULT -1,
    `drape_y` INT NOT NULL DEFAULT -1,
    `hall_of_fame_x` INT NOT NULL DEFAULT -1,
    `hall_of_fame_y` INT NOT NULL DEFAULT -1,
    `hall_of_fame_enabled` TINYINT(1) NOT NULL DEFAULT 0,
    `hall_of_fame_mode` VARCHAR(32) NOT NULL DEFAULT 'latest_registered',
    `hall_of_fame_currency_type` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `hotelview_landing_settings` (`id`, `background_url`, `left_url`, `right_url`, `drape_url`)
VALUES (1, '', '', '', '');

CREATE TABLE IF NOT EXISTS `hotelview_landing_slots` (
    `id` TINYINT UNSIGNED NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `type` VARCHAR(48) NOT NULL DEFAULT 'promotion',
    `title` VARCHAR(100) NOT NULL DEFAULT '',
    `body` VARCHAR(500) NOT NULL DEFAULT '',
    `image_url` VARCHAR(512) NOT NULL DEFAULT '',
    `button_text` VARCHAR(100) NOT NULL DEFAULT '',
    `link` VARCHAR(512) NOT NULL DEFAULT '',
    `progress` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `progress_label` VARCHAR(100) NOT NULL DEFAULT '',
    `config_json` TEXT NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `hotelview_landing_slots`
    MODIFY COLUMN `type` VARCHAR(48) NOT NULL DEFAULT 'promotion';

ALTER TABLE `hotelview_landing_slots`
    ADD COLUMN IF NOT EXISTS `config_json` TEXT NOT NULL AFTER `progress_label`;

ALTER TABLE `hotelview_landing_settings`
    ADD COLUMN IF NOT EXISTS `hall_of_fame_enabled` TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `hall_of_fame_mode` VARCHAR(32) NOT NULL DEFAULT 'latest_registered',
    ADD COLUMN IF NOT EXISTS `hall_of_fame_currency_type` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `left_x` INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `left_y` INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `right_x` INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `right_y` INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `drape_x` INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `drape_y` INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `hall_of_fame_x` INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `hall_of_fame_y` INT NOT NULL DEFAULT -1;

CREATE TABLE IF NOT EXISTS `hotelview_landing_votes` (
    `slot_id` TINYINT UNSIGNED NOT NULL,
    `user_id` INT UNSIGNED NOT NULL,
    `option_id` TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (`slot_id`, `user_id`),
    KEY `hotelview_landing_votes_option` (`slot_id`, `option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `hotelview_landing_slots` (`id`, `enabled`, `type`, `title`, `body`, `image_url`, `button_text`, `link`, `progress`, `progress_label`, `config_json`) VALUES
(1, 1, 'bonus', 'Bonus Bag II every 120 credits!', '', '', 'Get Credits', 'catalog/open/credits', 100, 'Only 120/120 credits to go!', '{}'),
(2, 1, 'promotion', 'Welcome to the Hotel', 'Discover rooms, events and the latest additions to the catalogue.', '', 'Open Navigator', 'navigator/show', 0, '', '{}'),
(3, 1, 'promotion', 'What''s new?', 'Check out the latest furni, offers and activities.', '', 'Open Catalogue', 'catalog/open', 0, '', '{}'),
(4, 1, 'promotion', 'Meet new friends', 'Visit public rooms and discover the Habbo community.', '', 'Find rooms', 'navigator/show', 0, '', '{}'),
(5, 1, 'promotion', 'Need help?', 'Read the Hotel Guide to learn how everything works.', '', 'Open Help', 'help/show', 0, '', '{}');

DELETE FROM `hotelview_landing_votes` WHERE `slot_id` > 5;
DELETE FROM `hotelview_landing_slots` WHERE `id` > 5;
