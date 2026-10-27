-- Room backgrounds (room ads furni) may only load their image from these websites: a comma-separated list of hosts,
-- subdomains included. Empty allows any website (the old behaviour). Example: habbo.com,imgur.com,yourhotel.com
-- Read on every save, so :update_config applies a change.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('room.background.allowed_hosts', '', 'Room background image hosts, comma separated (subdomains included). Empty = any website.')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('hotel.error.roomads.invalid', 'The room background was not saved: check the numbers and the image address.')
ON DUPLICATE KEY UPDATE `value` = `value`;
