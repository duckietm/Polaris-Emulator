-- Habbi : la maison des jeux (morpion, puissance 4, bataille navale, échecs, poker), dans le Game Center. Les parties
-- se jouent en mémoire ; les résultats sont comptés ici. Idempotent.
CREATE TABLE IF NOT EXISTS `hall_stats` (
  `user_id` int(11) NOT NULL,
  `game` varchar(20) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `game`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
  ('hall.enabled', 'true', 'Maison des jeux (morpion, puissance 4, bataille navale, échecs, poker) dans le Game Center.'),
  ('hall.tables.max', '40', 'Maison des jeux : nombre de tables ouvertes en même temps.'),
  ('hall.turn.seconds', '60', 'Maison des jeux : temps pour jouer un coup (après, un coup est joué pour le joueur).');
