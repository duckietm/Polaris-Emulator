-- Game socket limits per visitor address (the address a trusted proxy forwards, else the socket peer; an IPv6
-- visitor is its /64). A trusted proxy that forwards no address is not limited. 0 turns a limit off.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('ws.connections.per_ip', '20', 'Open game connections one visitor address may hold. 0 = no limit.'),
    ('login.sso.max_failures', '10', 'Failed ticket logins from one address before it is blocked. 0 = no limit.'),
    ('login.sso.failure_window_sec', '300', 'Window, in seconds, that login.sso.max_failures counts over.'),
    ('login.sso.block_sec', '600', 'Seconds an address may not log in after too many failed ticket logins.')
ON DUPLICATE KEY UPDATE `value` = `value`;
