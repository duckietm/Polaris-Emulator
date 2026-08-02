-- Forward-fix early Catalog Studio installations that applied the draft schema
-- before NORMAL and BUILDER catalogs were isolated in the same version.

ALTER TABLE `catalog_version_pages`
  ADD COLUMN IF NOT EXISTS `catalog_type` enum('NORMAL','BUILDER') NOT NULL DEFAULT 'NORMAL' AFTER `version_id`,
  DROP INDEX IF EXISTS `uq_catalog_version_page`,
  ADD UNIQUE KEY `uq_catalog_version_page` (`version_id`,`catalog_type`,`page_id`),
  DROP INDEX IF EXISTS `idx_catalog_version_pages_parent`,
  ADD KEY `idx_catalog_version_pages_parent` (`version_id`,`catalog_type`,`parent_id`,`order_num`);

ALTER TABLE `catalog_version_offers`
  ADD COLUMN IF NOT EXISTS `catalog_type` enum('NORMAL','BUILDER') NOT NULL DEFAULT 'NORMAL' AFTER `version_id`,
  DROP INDEX IF EXISTS `uq_catalog_version_offer`,
  ADD UNIQUE KEY `uq_catalog_version_offer` (`version_id`,`catalog_type`,`offer_id`),
  DROP INDEX IF EXISTS `idx_catalog_version_offers_page`,
  ADD KEY `idx_catalog_version_offers_page` (`version_id`,`catalog_type`,`page_id`,`order_number`);

ALTER TABLE `catalog_id_sequences`
  ADD COLUMN IF NOT EXISTS `catalog_type` enum('NORMAL','BUILDER') NOT NULL DEFAULT 'NORMAL' AFTER `entity_type`,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`entity_type`,`catalog_type`);

ALTER TABLE `catalog_change_entries`
  ADD COLUMN IF NOT EXISTS `catalog_type` enum('NORMAL','BUILDER') NOT NULL DEFAULT 'NORMAL' AFTER `entity_type`,
  DROP INDEX IF EXISTS `idx_catalog_change_entries_entity`,
  ADD KEY `idx_catalog_change_entries_entity` (`catalog_type`,`entity_type`,`entity_id`);

ALTER TABLE `catalog_edit_locks`
  ADD COLUMN IF NOT EXISTS `catalog_type` enum('NORMAL','BUILDER') NOT NULL DEFAULT 'NORMAL' AFTER `version_id`,
  DROP INDEX IF EXISTS `uq_catalog_edit_lock`,
  ADD UNIQUE KEY `uq_catalog_edit_lock` (`version_id`,`catalog_type`,`entity_type`,`entity_id`);

CREATE TABLE IF NOT EXISTS `catalog_operations` (
  `operation_id` varchar(96) NOT NULL,
  `actor_id` int NOT NULL,
  `version_id` bigint NOT NULL,
  `source` enum('UI','JSONC','SQL','RESTORE','UNDO') NOT NULL,
  `result_revision` bigint NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT current_timestamp(3),
  PRIMARY KEY (`operation_id`,`actor_id`),
  KEY `idx_catalog_operations_version` (`version_id`,`created_at`),
  CONSTRAINT `fk_catalog_operations_version_compat`
    FOREIGN KEY (`version_id`) REFERENCES `catalog_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `catalog_id_sequences` (`entity_type`, `catalog_type`, `next_id`)
SELECT 'PAGE', 'BUILDER', COALESCE(MAX(`id`), 0) + 1 FROM `catalog_pages_bc`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`));

INSERT INTO `catalog_id_sequences` (`entity_type`, `catalog_type`, `next_id`)
SELECT 'OFFER', 'BUILDER', COALESCE(MAX(`id`), 0) + 1 FROM `catalog_items_bc`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`));

SET @catalog_active_version_id = (
  SELECT `active_version_id` FROM `catalog_runtime_state` WHERE `singleton_id` = 1
);

SET @catalog_draft_version_id = (
  SELECT `draft_version_id` FROM `catalog_runtime_state` WHERE `singleton_id` = 1
);

INSERT INTO `catalog_version_pages`
  (`version_id`, `catalog_type`, `page_id`, `parent_id`, `caption_save`, `caption`, `page_layout`,
   `icon_color`, `icon_image`, `min_rank`, `order_num`, `visible`, `enabled`,
   `club_only`, `catalog_mode`, `vip_only`, `page_headline`, `page_teaser`,
   `page_special`, `page_text1`, `page_text2`, `page_text_details`,
   `page_text_teaser`, `room_id`, `includes`)
SELECT @catalog_active_version_id, 'BUILDER', `id`, `parent_id`, '', `caption`, `page_layout`,
       `icon_color`, `icon_image`, 1, `order_num`, CAST(`visible` AS UNSIGNED),
       CAST(`enabled` AS UNSIGNED), 0, 'BUILDER', 0, `page_headline`, `page_teaser`,
       COALESCE(`page_special`, ''), `page_text1`, `page_text2`, `page_text_details`,
       `page_text_teaser`, 0, ''
FROM `catalog_pages_bc`
WHERE @catalog_active_version_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `catalog_version_pages`
    WHERE `version_id` = @catalog_active_version_id AND `catalog_type` = 'BUILDER'
  );

INSERT INTO `catalog_version_offers`
  (`version_id`, `catalog_type`, `offer_id`, `item_ids`, `page_id`, `catalog_name`, `cost_credits`,
   `cost_points`, `points_type`, `amount`, `limited_stack`, `order_number`,
   `offer_id_client`, `song_id`, `extradata`, `have_offer`, `club_only`)
SELECT @catalog_active_version_id, 'BUILDER', `id`, `item_ids`, `page_id`, `catalog_name`, 0,
       0, 0, 1, 0, `order_number`, -1, 0, `extradata`, 1, 0
FROM `catalog_items_bc`
WHERE @catalog_active_version_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `catalog_version_offers`
    WHERE `version_id` = @catalog_active_version_id AND `catalog_type` = 'BUILDER'
  );

INSERT INTO `catalog_version_pages`
SELECT @catalog_draft_version_id, `catalog_type`, `page_id`, `parent_id`, `caption_save`, `caption`, `page_layout`,
       `icon_color`, `icon_image`, `min_rank`, `order_num`, `visible`, `enabled`, `club_only`,
       `catalog_mode`, `vip_only`, `page_headline`, `page_teaser`, `page_special`, `page_text1`,
       `page_text2`, `page_text_details`, `page_text_teaser`, `room_id`, `includes`
FROM `catalog_version_pages`
WHERE `version_id` = @catalog_active_version_id
  AND `catalog_type` = 'BUILDER'
  AND @catalog_draft_version_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `catalog_version_pages`
    WHERE `version_id` = @catalog_draft_version_id AND `catalog_type` = 'BUILDER'
  );

INSERT INTO `catalog_version_offers`
SELECT @catalog_draft_version_id, `catalog_type`, `offer_id`, `item_ids`, `page_id`, `catalog_name`,
       `cost_credits`, `cost_points`, `points_type`, `amount`, `limited_stack`, `order_number`,
       `offer_id_client`, `song_id`, `extradata`, `have_offer`, `club_only`
FROM `catalog_version_offers`
WHERE `version_id` = @catalog_active_version_id
  AND `catalog_type` = 'BUILDER'
  AND @catalog_draft_version_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `catalog_version_offers`
    WHERE `version_id` = @catalog_draft_version_id AND `catalog_type` = 'BUILDER'
  );
