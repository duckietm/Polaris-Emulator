ALTER TABLE `catalog_change_groups`
  MODIFY COLUMN `source` enum('UI','SQL','LIVE_SYNC','RESTORE','UNDO') NOT NULL;

ALTER TABLE `catalog_operations`
  MODIFY COLUMN `source` enum('UI','SQL','LIVE_SYNC','RESTORE','UNDO') NOT NULL;
