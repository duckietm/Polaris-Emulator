-- SSO tickets are single-use: the emulator consumes (clears) users.auth_ticket
-- right after a successful login so a captured ticket cannot be replayed.
-- debug_sso = 1 keeps tickets alive after login for local debugging (repeated
-- ?sso= logins with the same ticket). Default 0 = consume on login.
-- ON DUPLICATE KEY UPDATE value=value keeps any tuned value intact.
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
	('debug_sso', '0')
ON DUPLICATE KEY UPDATE `value` = `value`;
