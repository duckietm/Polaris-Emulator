-- NULL preserves compatibility with CMSes that do not issue expiring tickets yet.

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'auth_ticket_expires_at'
);

SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `users` ADD COLUMN `auth_ticket_expires_at` TIMESTAMP NULL DEFAULT NULL AFTER `auth_ticket`',
    'SELECT ''auth_ticket_expires_at already present, skipping'' AS info'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- Copy legacy websocket values only when the replacement key is absent. Keeping
-- the old aliases is harmless and avoids a duplicate-key failure on hotels
-- where an operator or earlier update already created both names.
INSERT INTO emulator_settings (`key`, `value`)
SELECT 'ws.whitelist', legacy.`value`
FROM emulator_settings legacy
WHERE legacy.`key` = 'websockets.whitelist'
  AND NOT EXISTS (
      SELECT 1 FROM emulator_settings current_setting
      WHERE current_setting.`key` = 'ws.whitelist'
  );

INSERT INTO emulator_settings (`key`, `value`)
SELECT 'ws.host', legacy.`value`
FROM emulator_settings legacy
WHERE legacy.`key` = 'ws.nitro.host'
  AND NOT EXISTS (
      SELECT 1 FROM emulator_settings current_setting
      WHERE current_setting.`key` = 'ws.host'
  );

INSERT INTO emulator_settings (`key`, `value`)
SELECT 'ws.port', legacy.`value`
FROM emulator_settings legacy
WHERE legacy.`key` = 'ws.nitro.port'
  AND NOT EXISTS (
      SELECT 1 FROM emulator_settings current_setting
      WHERE current_setting.`key` = 'ws.port'
  );
INSERT INTO emulator_settings (`key`, `value`)
SELECT 'ws.ip.header', 'X-Forwarded-For'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM emulator_settings current_setting
    WHERE current_setting.`key` = 'ws.ip.header'
);

INSERT INTO emulator_settings (`key`, `value`)
SELECT 'ws.enabled', 'true'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM emulator_settings current_setting
    WHERE current_setting.`key` = 'ws.enabled'
);
