-- Fortune wheel on/off switch. 0 = players can no longer open the wheel, spin or buy spins (the emulator ignores those
-- requests; the prize editor for acc_wheeladmin keeps working). Read on every request, so :update_config switches it.
-- Hide the toolbar button too with "toolbar.fortunewheel.enabled": false in the client's ui-config.json.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wheel.enabled', '1', 'Fortune wheel: 1 = on, 0 = off (no opening, spinning or buying spins). Also set toolbar.fortunewheel.enabled in the client.')
ON DUPLICATE KEY UPDATE `value` = `value`;
