-- Soundboard
-- The room flag column + sounds table are also created at boot by

ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `soundboard_enabled` TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS `soundboard_sounds` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL DEFAULT '',            -- pad label shown in the client
    `url` VARCHAR(255) NOT NULL DEFAULT '',            -- audio url (uploaded via CMS, like custom badges)
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Fortune Wheel
-- Tables are also created at boot by WheelManager (CREATE TABLE IF NOT EXISTS),
-- so applying this file is only needed to seed prizes + settings.

CREATE TABLE IF NOT EXISTS `wheel_prizes` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(16) NOT NULL DEFAULT 'nothing',   -- item | badge | credits | points | spin | nothing
    `value` VARCHAR(64) NOT NULL DEFAULT '',          -- item: base item id ; badge: badge code ; others: unused
    `amount` INT(11) NOT NULL DEFAULT 1,              -- item qty / credits / points / extra spins
    `points_type` INT(11) NOT NULL DEFAULT 5,         -- for type=points (diamond default 5)
    `weight` INT(11) NOT NULL DEFAULT 1,              -- relative probability
    `label` VARCHAR(64) NOT NULL DEFAULT '',          -- slice label override (optional)
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `wheel_user_state` (
    `user_id` INT(11) NOT NULL,
    `free_spins` INT(11) NOT NULL DEFAULT 0,          -- remaining free spins for the current day
    `extra_spins` INT(11) NOT NULL DEFAULT 0,         -- bought / won spins
    `last_reset` INT(11) NOT NULL DEFAULT 0,          -- day index of last daily reset (unix / 86400)
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `wheel_recent_wins` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `user_id` INT(11) NOT NULL,
    `username` VARCHAR(64) NOT NULL DEFAULT '',
    `look` VARCHAR(255) NOT NULL DEFAULT '',
    `prize_label` VARCHAR(64) NOT NULL DEFAULT '',
    `won_at` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_wheel_recent_wins_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wheel.free_spins_per_day', '1',  'Fortune wheel: free spins granted each day.')
    ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wheel.spin_cost', '50',          'Fortune wheel: cost of one extra spin.')
    ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wheel.spin_cost_type', '5',      'Fortune wheel: currency type for the spin cost (5 = diamonds; -1 = credits).')
    ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);


INSERT INTO `wheel_prizes` (`type`, `amount`, `points_type`, `weight`, `label`, `sort_order`) VALUES
    ('points',25, 5, 20, '25 diamonds',1),
    ('points',50, 5, 12, '50 diamonds',2),
    ('points',200, 5,  3, '200 diamonds',3),
    ('credits',100, 0, 15, '100 credits',4),
    ('spin',1, 0, 15, '1 Extra spin', 5),
    ('spin',2, 0,  6, '2 Extra spins',6),
    ('nothing',0, 0, 29, 'Oh to bad!',7);
	
INSERT INTO `permission_definitions`
  (`permission_key`, `max_value`, `comment`,
   `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
  ('acc_wheeladmin', 1, 'Required to open the Fortune Wheel settings popup and edit prize rows.',
   0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);
