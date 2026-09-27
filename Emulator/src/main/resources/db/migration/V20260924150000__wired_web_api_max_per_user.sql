-- How many Variables Web API add-ons one user may own (inventory and rooms together). A room still
-- holds at most one, whatever this says. A hotel that set it by hand keeps its value.
INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wired.api.max_per_user', '1', 'Variables Web API add-ons one user may own, 1 to 100. A room always holds at most one.')
ON DUPLICATE KEY UPDATE `value` = `value`;
