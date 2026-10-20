-- Habbo caps the different variables one furni or user may hold (wired monitor error 6, TOO_MANY_VARIABLES); past it a
-- new variable is not given. Habbo does not publish the number, so the default sits well above the permanent caps.
-- The variable fx boxes a room draws are capped as well (error 8, VARIABLE_FX_LIMIT); boxes past it stay in the room
-- and are left out. A hotel that set either value by hand keeps it.
INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wired.variables.holder.max', '200', 'Different wired variables one furni or user may hold; a new one past it is not given and the monitor counts TOO_MANY_VARIABLES. 0 turns the cap off.'),
    ('wired.variablefx.max.boxes', '25', 'Variable fx boxes a room draws; the rest are left out and the monitor counts VARIABLE_FX_LIMIT.')
ON DUPLICATE KEY UPDATE `value` = `value`;
