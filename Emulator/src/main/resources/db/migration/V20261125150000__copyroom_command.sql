-- :copyroom copies the room the user stands in (settings, furni, wired, floor plan, bots) into a new room of
-- their own. It creates furni, so only the top rank has it by default; a hotel that already set the permission
-- keeps its own choice.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`, `rank_7`) VALUES
	('cmd_copyroom', 1, 'Allows :copyroom to copy the current room, with its furni, wired and bots, into a new room owned by the user.', 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
	('commands.keys.cmd_copyroom', 'copyroom'),
	('commands.description.cmd_copyroom', ':copyroom - copy this room into a new room of your own'),
	('commands.success.cmd_copyroom', 'Room copied as room %roomid% (%items% furni, %bots% bots).'),
	('commands.error.cmd_copyroom.no_room', 'You have to be in a room to copy it.'),
	('commands.error.cmd_copyroom.failed', 'The room could not be copied. Nothing was changed.')
ON DUPLICATE KEY UPDATE `value` = `value`;
