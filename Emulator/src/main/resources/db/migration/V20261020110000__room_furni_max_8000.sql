-- Habbo's caps are 4000 floor and 4000 wall furni, so the room cap on all furni of a room becomes 8000; at 2500 it
-- refused floor furni long before the floor cap. Only a hotel still on the old default 2500 is raised; a value set
-- by hand is kept.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.room.furni.max', '8000', 'Maximum amount of furniture (floor and wall) allowed in one room; a new floor furni is also refused at hotel.room.floorfurni.max.')
ON DUPLICATE KEY UPDATE `value` = IF(`value` = '2500', '8000', `value`);
