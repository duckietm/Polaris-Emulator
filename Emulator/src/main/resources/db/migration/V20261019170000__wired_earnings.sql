-- Credits wired hands out (a reward contract, a trade contract's reward, Give Currency From Chest) wait in the
-- earnings window until the player claims them, as in Habbo: the boxes' earnings category picks the row, 11 games
-- or 13 agency.
CREATE TABLE IF NOT EXISTS `users_wired_earnings` (
  `user_id` int(11) NOT NULL,
  `category` varchar(32) NOT NULL,
  `credits` int(11) NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
('earnings.agency.enabled', '1', 'Enable the agency earnings row (credits wired gives with earnings category 13).'),
('earnings.agency.native.enabled', '1', 'The agency earnings row holds the credits wired gives (0 pays them straight into the purse).');

-- A hotel that configured a periodic games reward keeps it; wired credits of the games category then go straight
-- into the purse, as before.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`)
SELECT
  'earnings.games.native.enabled',
  IF(COUNT(*) > 0, '0', '1'),
  'The games earnings row holds the credits wired gives (0 keeps the configured periodic games reward and pays wired credits into the purse).'
FROM (
  SELECT `key` FROM `emulator_settings`
  WHERE `key` IN ('earnings.games.credits', 'earnings.games.pixels', 'earnings.games.points', 'earnings.games.badge',
                  'earnings.games.item_id', 'earnings.games.hc.days')
    AND TRIM(`value`) NOT IN ('', '0')
) AS `configured_games_rewards`;
