-- Habbi : le Loup-Garou (Game Center). Les parties se jouent en mémoire ; les victoires sont comptées ici. Idempotent.
CREATE TABLE IF NOT EXISTS `werewolf_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
  ('werewolf.enabled', 'true', 'Loup-Garou dans le Game Center.'),
  ('werewolf.games.max', '10', 'Loup-Garou : nombre de villages ouverts en même temps.');
