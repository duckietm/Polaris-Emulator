-- AIR catalogue "new additions": the catalog index flags new additions while the new_additions page offers something
-- newer than what the player saw there. The mark is the newest catalog_items id of that page when the player last
-- opened the catalogue with the flag set (MarkCatalogNewAdditionsPageOpened).
CREATE TABLE IF NOT EXISTS `users_catalog_new_additions` (
  `user_id` int(11) NOT NULL,
  `last_seen_offer_id` int(11) NOT NULL DEFAULT 0,
  `seen_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
('catalog.new_additions.enabled', '1', 'Flag new offers on the new_additions catalog page in the catalog index (opens that page and lights the toolbar label).');
