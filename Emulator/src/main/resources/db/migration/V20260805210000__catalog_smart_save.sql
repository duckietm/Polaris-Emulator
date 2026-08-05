ALTER TABLE `catalog_operations`
  ADD COLUMN `request_fingerprint` char(64) NULL AFTER `result_revision`,
  ADD COLUMN `action` varchar(24) NULL AFTER `request_fingerprint`,
  ADD COLUMN `entity_type` enum('PAGE','OFFER') NULL AFTER `action`,
  ADD COLUMN `catalog_type` enum('NORMAL','BUILDER') NULL AFTER `entity_type`,
  ADD COLUMN `entity_id` int NULL AFTER `catalog_type`,
  ADD COLUMN `history_group_id` bigint NULL AFTER `entity_id`,
  ADD COLUMN `result_json` mediumtext NULL AFTER `history_group_id`;
