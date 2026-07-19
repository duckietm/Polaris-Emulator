-- Audit trail for furnidata name/description edits made through the furni editor,
-- plus config keys for the editor write path. NOTE: *.enabled keys elsewhere are
-- read via Boolean.parseBoolean (true/false), but these two are numeric.
CREATE TABLE IF NOT EXISTS `furnidata_edit_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `classname` varchar(255) NOT NULL,
  `action` enum('edit','revert') NOT NULL DEFAULT 'edit',
  `old_name` varchar(256) NOT NULL DEFAULT '',
  `new_name` varchar(256) NOT NULL DEFAULT '',
  `old_description` varchar(256) NOT NULL DEFAULT '',
  `new_description` varchar(256) NOT NULL DEFAULT '',
  `timestamp` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_classname` (`classname`),
  INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `emulator_settings` (`key`,`value`) VALUES
('items.furnidata.edit.backup.keep','10'),
('items.furnidata.edit.ratelimit.ms','2000'),
-- Server-authoritative furni names (source of truth = furnidata JSON)
('items.furnidata.names.enabled','true'),
('items.furnidata.path',''),
('items.furnidata.max.bytes','67108864'),
-- Live-reload watcher
('items.furnidata.watch.enabled','true'),
('items.furnidata.watch.debounce.ms','750'),
('items.furnidata.watch.min.interval.ms','5000'),
('items.furnidata.delta.cap','500'),
-- Furni editor: import official names/descriptions from Habbo
('furni.editor.import.url','https://www.habbo.com/gamedata/furnidata_json/1'),
('furni.editor.import.cache.ms','600000');
