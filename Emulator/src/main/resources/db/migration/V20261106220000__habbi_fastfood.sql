-- Habbi : Fast Food (Game Center), le jeu d'origine de Habbo refait en HTML5. Les parties se jouent en mémoire ; les
-- scores sont comptés ici. Idempotent.
CREATE TABLE IF NOT EXISTS `fastfood_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  `stars` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
  ('fastfood.enabled', 'true', 'Fast Food (HTML5) dans le Game Center.'),
  ('fastfood.games.max', '10', 'Fast Food : nombre de parties ouvertes en même temps.');
