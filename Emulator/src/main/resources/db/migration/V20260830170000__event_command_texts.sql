-- Feedback and usage texts for the ":event" command.
--
-- The command used to return false whenever it was typed outside a room or with
-- no message, which the command handler turns into silence: the staff member saw
-- nothing at all. It now whispers, so every branch needs a text.
--
-- INSERT IGNORE keeps a text a hotel has already reworded or translated, so the
-- migration is safe to replay.
INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.error.cmd_event.noroom', 'You have to be inside the room that hosts the event.'),
    ('commands.error.cmd_event.usage', 'Announce an event with :event <message>, close it with :event off.'),
    ('commands.error.cmd_event.started', 'The event in %room% has been announced to the hotel.'),
    ('commands.error.cmd_event.ended', 'The hotel has been told that the event in %room% is over.');

-- The command learned a second form. The WHERE keeps a customised usage line.
UPDATE `emulator_texts`
SET `value` = ':event <message> | :event off'
WHERE `key` = 'commands.description.cmd_event'
    AND `value` = ':event <message>';
