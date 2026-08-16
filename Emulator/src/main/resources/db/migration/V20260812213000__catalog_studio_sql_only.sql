UPDATE `catalog_change_groups`
SET `source` = 'SQL'
WHERE `source` = 'JSONC';

UPDATE `catalog_operations`
SET `source` = 'SQL'
WHERE `source` = 'JSONC';

ALTER TABLE `catalog_change_groups`
  MODIFY COLUMN `source` enum('UI','SQL','RESTORE','UNDO') NOT NULL;

ALTER TABLE `catalog_operations`
  MODIFY COLUMN `source` enum('UI','SQL','RESTORE','UNDO') NOT NULL;
