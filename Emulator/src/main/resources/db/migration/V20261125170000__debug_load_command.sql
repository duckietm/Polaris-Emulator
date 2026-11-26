-- :debugload [amount | clear] fills the current room with walking, talking test bots that all look different
-- (500 by default), to see how a client holds up in a crowd. The bots exist in memory only.
--
-- Two locks, both closed by default for everyone but the top rank: the permission, and the emulator setting
-- debug_load, which stays 0 until a hotel turns it on. A hotel that already has either keeps its own value.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`, `rank_7`) VALUES
	('cmd_debug_load', 1, 'Allows :debugload to fill the current room with test bots (needs the emulator setting debug_load = 1).', 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
	('debug_load', '0', 'Enables the :debugload command (test bots for load testing a room); 0: the command refuses.')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
	('commands.keys.cmd_debug_load', 'debugload'),
	('commands.description.cmd_debug_load', ':debugload [amount | clear] - fill this room with test bots'),
	('commands.success.cmd_debug_load', '%amount% test bots are walking in. :debugload clear removes them.'),
	('commands.success.cmd_debug_load.cleared', '%amount% test bots removed.'),
	('commands.error.cmd_debug_load.disabled', 'The load test is switched off (emulator setting debug_load).'),
	('commands.error.cmd_debug_load.no_room', 'You have to be in a room to load test it.'),
	('commands.error.cmd_debug_load.usage', 'Use :debugload, :debugload <amount> or :debugload clear.')
ON DUPLICATE KEY UPDATE `value` = `value`;
