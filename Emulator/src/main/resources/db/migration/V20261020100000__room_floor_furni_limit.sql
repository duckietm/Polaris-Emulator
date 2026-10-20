-- Habbo caps floor furni apart from wall furni (4000 each). A new floor furni, a wired box included, is refused once a
-- room holds this many; a room already over it keeps its furni. A hotel that set the value by hand keeps it.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.room.floorfurni.max', '4000', 'Maximum amount of floor furniture allowed in one room (Habbo: 4000). Rooms already over it keep their furni.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- The room furni cap stays the hotel cap on all furni of a room: a floor furni is refused at whichever of the two
-- caps is reached first. Only the comment changes; the value a hotel runs with is kept.
UPDATE `emulator_settings`
SET `comment` = 'Maximum amount of furniture (floor and wall) allowed in one room; a new floor furni is also refused at hotel.room.floorfurni.max.'
WHERE `key` = 'hotel.room.furni.max';
