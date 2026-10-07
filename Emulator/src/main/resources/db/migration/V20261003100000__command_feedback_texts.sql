-- Feedback texts for chat commands.
--
-- A command that failed, was used without its permission or got bad input used
-- to be said in the room as plain chat. It is now answered with a whisper, so
-- those branches need a text. The code falls back to the same English lines.
--
-- ON DUPLICATE KEY UPDATE value=value keeps a text a hotel already reworded.
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
	('commands.error.generic', 'Something went wrong running that command.'),
	('commands.error.no_permission', 'You do not have permission to use this command.'),
	('commands.error.cmd_shutdown.usage', 'Usage: :shutdown <minutes 1-1440> [reason], :shutdown now [reason] or :shutdown cancel'),
	('commands.error.cmd_shutdown.none_pending', 'There is no pending shutdown to cancel.'),
	('commands.succes.cmd_shutdown.cancelled', 'The pending shutdown has been cancelled.'),
	('commands.error.cmd_calendar.not_found', 'That calendar campaign does not exist.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- The commands learned new forms. The WHERE keeps a customised usage line.
UPDATE `emulator_texts`
SET `value` = ':shutdown <minutes|now|cancel> [reason]'
WHERE `key` = 'commands.description.cmd_shutdown'
	AND `value` = ':shutdown';

UPDATE `emulator_texts`
SET `value` = ':mute <username> [seconds]'
WHERE `key` = 'commands.description.cmd_mute'
	AND `value` = ':mute <username>';
