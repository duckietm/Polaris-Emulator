-- ws.ip.header.trusted and ws.whitelist were added directly to the applied
-- baseline, which changes its Flyway checksum and makes every existing hotel
-- fail validation on the next start. The baseline is restored and the two
-- settings move forward into this migration instead.
-- ON DUPLICATE KEY UPDATE value=value keeps any tuned value intact.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
	('ws.ip.header.trusted', '0.0.0.0', 'If you use your own proxy server like Traefik then put the proxy IP here if not leave blanc'),
	('ws.whitelist', '', 'Place here the API domainname')
ON DUPLICATE KEY UPDATE `value` = `value`;
