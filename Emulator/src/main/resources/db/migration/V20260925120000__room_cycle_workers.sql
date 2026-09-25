-- Room cycles run on their own workers, picked per room by id, so one lagging room no longer holds up
-- the whole hotel. 0 = one worker per processor (at least 2, at most 16). Changes need a restart.
-- A hotel that set these by hand keeps its values.
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('room.cycle.workers', '0'),
    ('room.cycle.slow_ms', '250')
ON DUPLICATE KEY UPDATE `value` = `value`;
