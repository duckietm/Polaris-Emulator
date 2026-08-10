-- Official catalog page identities can exceed the legacy 25-character limit.
-- Keep the live projection and version snapshots lossless and Unicode-safe.
ALTER TABLE `catalog_pages`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  MODIFY COLUMN `caption_save` varchar(128) NOT NULL DEFAULT '';

ALTER TABLE `catalog_items`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `catalog_pages_bc`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `catalog_items_bc`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `catalog_version_pages`
  MODIFY COLUMN `caption_save` varchar(128) NOT NULL DEFAULT '';
