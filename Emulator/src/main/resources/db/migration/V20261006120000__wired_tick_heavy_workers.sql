-- Workers for rooms the wired monitor marks heavy: their timers and signal chains run there, so they
-- cannot slow down the other rooms. 0 keeps heavy rooms on their usual worker. A hotel that set it
-- by hand keeps its value.
INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wired.tick.heavy.workers', '1', 'Wired workers for rooms marked heavy, 0 to 8. 0 keeps heavy rooms on their usual worker.')
ON DUPLICATE KEY UPDATE `value` = `value`;
