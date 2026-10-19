-- The wired menu monitor shows the permanent furni, user and global variables of a room against these
-- limits (Habbo: 23, 93 and 70). A hotel that set them by hand keeps its values.
INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wired.monitor.permanent.furni.variables.limit', '23', 'Permanent furni variables a room may hold, shown on the wired monitor (Habbo: 23).'),
    ('wired.monitor.permanent.user.variables.limit', '93', 'Permanent user variables a room may hold, shown on the wired monitor (Habbo: 93).'),
    ('wired.monitor.permanent.global.variables.limit', '70', 'Permanent global variables a room may hold, shown on the wired monitor (Habbo: 70).')
ON DUPLICATE KEY UPDATE `value` = `value`;
