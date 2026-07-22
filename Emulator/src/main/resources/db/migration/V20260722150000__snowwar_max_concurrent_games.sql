-- Cap on simultaneous SnowWar game sessions (protects server CPU).
-- While the cap is reached, queued players wait; matching resumes
-- automatically when a running game finishes. Default: 1 session.
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
	('gamecenter.snowwar.games.max.concurrent', '1')
ON DUPLICATE KEY UPDATE `value` = `value`;
