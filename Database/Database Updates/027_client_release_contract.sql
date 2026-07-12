INSERT INTO `emulator_settings` (`key`, `value`, `comment`)
VALUES ('client.release.allowed', 'NITRO-3-6-0', 'Comma-separated client release versions accepted before SSO login.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);
