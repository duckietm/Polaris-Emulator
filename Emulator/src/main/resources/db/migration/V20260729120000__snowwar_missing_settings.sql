-- SnowWar settings the code reads but earlier migrations never seeded. Each
-- already has a hardcoded fallback, so this only surfaces them in the DB so a
-- hotel owner can tune them without recompiling. Values match the code
-- defaults. ON DUPLICATE KEY UPDATE value=value keeps any tuned value intact.
--
--   enabled          - master on/off switch for the SnowWar game
--   players.min      - players required before a match can start
--   game.start.time  - lobby countdown (seconds) once enough players queue
--   map.model.1      - room_models name backing arena map 1
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
	('gamecenter.snowwar.enabled', '1'),
	('gamecenter.snowwar.players.min', '2'),
	('gamecenter.snowwar.game.start.time', '15'),
	('gamecenter.snowwar.map.model.1', 'snowstorm_arena_1')
ON DUPLICATE KEY UPDATE `value` = `value`;
