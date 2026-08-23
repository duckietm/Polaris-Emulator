UPDATE `emulator_settings`
SET `value` = '/var/www/Octane-UI/dist/configuration/renderer-config.json'
WHERE `key` = 'furni.editor.renderer.config.path'
  AND `value` = '/var/www/Nitro-V3/dist/configuration/renderer-config.json';

UPDATE `emulator_settings`
SET `comment` = REPLACE(`comment`, 'NitroV3 Login', 'OctaneUI Login')
WHERE `key` IN ('new_user_credits', 'new_user_diamonds', 'new_user_duckets')
  AND `comment` LIKE '%NitroV3 Login%';