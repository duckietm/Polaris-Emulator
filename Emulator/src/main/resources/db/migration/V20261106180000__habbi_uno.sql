-- Habbi : UNO (Game Center). Les parties se jouent en mémoire ; les victoires sont comptées ici. Idempotent.
CREATE TABLE IF NOT EXISTS `uno_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
  ('uno.enabled', 'true', 'UNO dans le Game Center.'),
  ('uno.turn.seconds', '25', 'UNO : secondes pour jouer son tour (passé ce délai, le joueur pioche et passe ; trois fois de suite, il quitte la table).'),
  ('uno.tables.max', '20', 'UNO : nombre de tables ouvertes en même temps.'),
  ('uno.players.max', '6', 'UNO : joueurs au plus par table (2 à 6).');
