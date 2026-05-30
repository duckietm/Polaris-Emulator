ALTER TABLE `rooms`
    ADD COLUMN IF NOT EXISTS `soundboard_enabled` TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS `soundboard_sounds` (
    `id`         INT(11)      NOT NULL AUTO_INCREMENT,
    `name`       VARCHAR(64)  NOT NULL DEFAULT '',   -- pad label shown in the client
    `url`        VARCHAR(255) NOT NULL DEFAULT '',   -- audio url (uploaded via CMS, like custom badges)
    `enabled`    TINYINT(1)   NOT NULL DEFAULT 1,
    `sort_order` INT(11)      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
--  Fortune Wheel — tables
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wheel_prizes` (
    `id`          INT(11)     NOT NULL AUTO_INCREMENT,
    `type`        VARCHAR(16) NOT NULL DEFAULT 'nothing', -- item | badge | credits | points | spin | nothing
    `value`       VARCHAR(64) NOT NULL DEFAULT '',        -- item: base item id ; badge: badge code ; others: unused
    `amount`      INT(11)     NOT NULL DEFAULT 1,          -- item qty / credits / points / extra spins
    `points_type` INT(11)     NOT NULL DEFAULT 5,          -- for type=points (diamond default 5)
    `weight`      INT(11)     NOT NULL DEFAULT 1,          -- relative probability
    `label`       VARCHAR(64) NOT NULL DEFAULT '',         -- slice label override (optional)
    `enabled`     TINYINT(1)  NOT NULL DEFAULT 1,
    `sort_order`  INT(11)     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `wheel_user_state` (
    `user_id`     INT(11) NOT NULL,
    `free_spins`  INT(11) NOT NULL DEFAULT 0,  -- remaining free spins for the current day
    `extra_spins` INT(11) NOT NULL DEFAULT 0,  -- bought / won spins
    `last_reset`  INT(11) NOT NULL DEFAULT 0,  -- day index of last daily reset (unix / 86400)
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `wheel_recent_wins` (
    `id`          INT(11)      NOT NULL AUTO_INCREMENT,
    `user_id`     INT(11)      NOT NULL,
    `username`    VARCHAR(64)  NOT NULL DEFAULT '',
    `look`        VARCHAR(255) NOT NULL DEFAULT '',
    `prize_label` VARCHAR(64)  NOT NULL DEFAULT '',
    `won_at`      INT(11)      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wheel.free_spins_per_day', '1',  'Fortune wheel: free spins granted each day.'),
    ('wheel.spin_cost',          '50', 'Fortune wheel: cost of one extra spin.'),
    ('wheel.spin_cost_type',     '5',  'Fortune wheel: currency type for the spin cost (5 = diamonds; -1 = credits).')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

INSERT INTO `wheel_prizes` (`type`, `amount`, `points_type`, `weight`, `label`, `sort_order`)
SELECT `type`, `amount`, `points_type`, `weight`, `label`, `sort_order`
FROM (
              SELECT 'points'  AS `type`,  25 AS `amount`, 5 AS `points_type`, 20 AS `weight`, '25 diamonds'   AS `label`, 1 AS `sort_order`
    UNION ALL SELECT 'points',   50, 5, 12, '50 diamonds',   2
    UNION ALL SELECT 'points',  200, 5,  3, '200 diamonds',  3
    UNION ALL SELECT 'credits', 100, 0, 15, '100 credits',   4
    UNION ALL SELECT 'spin',      1, 0, 15, '1 Extra spin',  5
    UNION ALL SELECT 'spin',      2, 0,  6, '2 Extra spins', 6
    UNION ALL SELECT 'nothing',   0, 0, 29, 'Oh to bad!',    7
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `wheel_prizes`);

INSERT IGNORE INTO `permission_definitions` (`permission_key`, `max_value`, `comment`)
VALUES (
    'acc_wheeladmin',
    1,
    'Allows opening the Fortune Wheel prize editor (FortuneWheelSettingsView) to add/edit prize slices. Gated server-side by the same key.'
);

SET @cols := NULL;
SELECT GROUP_CONCAT(CONCAT('dst.`', `column_name`, '` = src.`', `column_name`, '`') SEPARATOR ', ')
    INTO @cols
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND `table_name`   = 'permission_definitions'
  AND `column_name`  REGEXP '^rank_[0-9]+$';

SET @sql := CONCAT(
    'UPDATE `permission_definitions` dst ',
    'JOIN `permission_definitions` src ON src.`permission_key` = ''acc_ads_background'' ',
    'SET ', @cols, ' ',
    'WHERE dst.`permission_key` = ''acc_wheeladmin'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;