-- SnowWar (SnowStorm) server-side game settings.

INSERT INTO emulator_settings (`key`, `value`) VALUES
	('gamecenter.snowwar.game.length.seconds', '180'),
	('gamecenter.snowwar.preparing.seconds', '10'),
	('gamecenter.snowwar.restart.seconds', '30'),
	('gamecenter.snowwar.queue.match.max', '8')
ON DUPLICATE KEY UPDATE `value` = `value`;
