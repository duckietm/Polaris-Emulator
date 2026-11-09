-- Default location of the furnidata file on a standard Linux deployment, where the gamedata folder is served from
-- /var/www/gamedata. Only an empty value is filled: a hotel that already points items.furnidata.path elsewhere keeps
-- its own path, and the renderer config and asset base settings are still tried before this one.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('items.furnidata.path', '/var/www/gamedata/config/FurnitureData.json');

UPDATE `emulator_settings`
SET `value` = '/var/www/gamedata/config/FurnitureData.json'
WHERE `key` = 'items.furnidata.path' AND `value` = '';
