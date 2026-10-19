-- Habbo caps wall furni apart from floor furni (4000 each). New wall furni is refused once a room holds this
-- many; a room already over it keeps its furni. A hotel that set the value by hand keeps it.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.room.wallfurni.max', '4000', 'Maximum amount of wall furniture allowed in one room (Habbo: 4000). Rooms already over it keep their furni.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- The permanent variable limits are now enforced: saving a box as permanent over the limit is refused with
-- wiredfurni.error.variables.permanent_limit. Boxes that already are permanent keep working.
UPDATE `wired_emulator_settings`
SET `comment` = 'Permanent furni variables a room may hold; a new one over it is refused (Habbo: 23).'
WHERE `key` = 'wired.monitor.permanent.furni.variables.limit';

UPDATE `wired_emulator_settings`
SET `comment` = 'Permanent user variables a room may hold; a new one over it is refused (Habbo: 93).'
WHERE `key` = 'wired.monitor.permanent.user.variables.limit';

UPDATE `wired_emulator_settings`
SET `comment` = 'Permanent global variables a room may hold; a new one over it is refused (Habbo: 70).'
WHERE `key` = 'wired.monitor.permanent.global.variables.limit';
