-- Habbi : BattleBall (Game Center). Les parties se jouent en mémoire ; les scores sont comptés ici. Idempotent.
CREATE TABLE IF NOT EXISTS `battleball_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  `points` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
  ('battleball.enabled', 'true', 'BattleBall dans le Game Center.'),
  ('battleball.games.max', '10', 'BattleBall : nombre de parties ouvertes en même temps.');
