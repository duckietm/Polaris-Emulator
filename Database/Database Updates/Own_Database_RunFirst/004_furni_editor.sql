INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
  ('furni.editor.renderer.config.path', '/var/www/Octane-UI/dist/configuration/renderer-config.json'),
  ('furni.editor.asset.base.path', '/var/www/gamedata/furniture/');
  
ALTER TABLE permissions
ADD COLUMN `acc_catalogfurni` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `acc_catalog_ids`;