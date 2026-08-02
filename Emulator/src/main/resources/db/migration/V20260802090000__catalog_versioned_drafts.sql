-- Catalog Studio keeps one published snapshot and one shared draft.
-- The legacy catalog tables remain the live compatibility projection.

CREATE TABLE `catalog_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `status` enum('DRAFT','PUBLISHED','ARCHIVED') NOT NULL,
  `based_on_version_id` bigint DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT 0,
  `label` varchar(128) NOT NULL DEFAULT '',
  `created_by` int NOT NULL DEFAULT 0,
  `created_at` timestamp(3) NOT NULL DEFAULT current_timestamp(3),
  `published_by` int DEFAULT NULL,
  `published_at` timestamp(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_catalog_versions_status` (`status`,`id`),
  KEY `idx_catalog_versions_based_on` (`based_on_version_id`),
  CONSTRAINT `fk_catalog_versions_based_on`
    FOREIGN KEY (`based_on_version_id`) REFERENCES `catalog_versions` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `catalog_runtime_state` (
  `singleton_id` tinyint NOT NULL,
  `active_version_id` bigint NOT NULL,
  `draft_version_id` bigint NOT NULL,
  `updated_at` timestamp(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`singleton_id`),
  UNIQUE KEY `uq_catalog_runtime_active` (`active_version_id`),
  UNIQUE KEY `uq_catalog_runtime_draft` (`draft_version_id`),
  CONSTRAINT `chk_catalog_runtime_singleton` CHECK (`singleton_id` = 1),
  CONSTRAINT `fk_catalog_runtime_active`
    FOREIGN KEY (`active_version_id`) REFERENCES `catalog_versions` (`id`),
  CONSTRAINT `fk_catalog_runtime_draft`
    FOREIGN KEY (`draft_version_id`) REFERENCES `catalog_versions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `catalog_version_pages` (
  `version_id` bigint NOT NULL,
  `catalog_type` enum('NORMAL','BUILDER') NOT NULL,
  `page_id` int NOT NULL,
  `parent_id` int NOT NULL DEFAULT -1,
  `caption_save` varchar(25) NOT NULL DEFAULT '',
  `caption` varchar(128) NOT NULL,
  `page_layout` varchar(64) NOT NULL DEFAULT 'default_3x3',
  `icon_color` int NOT NULL DEFAULT 1,
  `icon_image` int NOT NULL DEFAULT 1,
  `min_rank` int NOT NULL DEFAULT 1,
  `order_num` int NOT NULL DEFAULT 1,
  `visible` tinyint(1) NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `club_only` tinyint(1) NOT NULL DEFAULT 0,
  `catalog_mode` enum('NORMAL','BUILDER','BOTH') NOT NULL DEFAULT 'NORMAL',
  `vip_only` tinyint(1) NOT NULL DEFAULT 0,
  `page_headline` varchar(1024) NOT NULL DEFAULT '',
  `page_teaser` varchar(64) NOT NULL DEFAULT '',
  `page_special` varchar(2048) NOT NULL DEFAULT '',
  `page_text1` text DEFAULT NULL,
  `page_text2` text DEFAULT NULL,
  `page_text_details` text DEFAULT NULL,
  `page_text_teaser` text DEFAULT NULL,
  `room_id` int NOT NULL DEFAULT 0,
  `includes` varchar(128) NOT NULL DEFAULT '',
  UNIQUE KEY `uq_catalog_version_page` (`version_id`,`catalog_type`,`page_id`),
  KEY `idx_catalog_version_pages_parent` (`version_id`,`catalog_type`,`parent_id`,`order_num`),
  CONSTRAINT `fk_catalog_version_pages_version`
    FOREIGN KEY (`version_id`) REFERENCES `catalog_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `catalog_version_offers` (
  `version_id` bigint NOT NULL,
  `catalog_type` enum('NORMAL','BUILDER') NOT NULL,
  `offer_id` int NOT NULL,
  `item_ids` varchar(666) NOT NULL,
  `page_id` int NOT NULL,
  `catalog_name` varchar(100) NOT NULL,
  `cost_credits` int NOT NULL DEFAULT 3,
  `cost_points` int NOT NULL DEFAULT 0,
  `points_type` int NOT NULL DEFAULT 0,
  `amount` int NOT NULL DEFAULT 1,
  `limited_stack` int NOT NULL DEFAULT 0,
  `order_number` int NOT NULL DEFAULT 1,
  `offer_id_client` int NOT NULL DEFAULT -1,
  `song_id` int unsigned NOT NULL DEFAULT 0,
  `extradata` varchar(500) NOT NULL DEFAULT '',
  `have_offer` tinyint(1) NOT NULL DEFAULT 1,
  `club_only` tinyint(1) NOT NULL DEFAULT 0,
  UNIQUE KEY `uq_catalog_version_offer` (`version_id`,`catalog_type`,`offer_id`),
  KEY `idx_catalog_version_offers_page` (`version_id`,`catalog_type`,`page_id`,`order_number`),
  CONSTRAINT `fk_catalog_version_offers_version`
    FOREIGN KEY (`version_id`) REFERENCES `catalog_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `catalog_id_sequences` (
  `entity_type` enum('PAGE','OFFER') NOT NULL,
  `catalog_type` enum('NORMAL','BUILDER') NOT NULL,
  `next_id` bigint NOT NULL,
  PRIMARY KEY (`entity_type`,`catalog_type`),
  CONSTRAINT `chk_catalog_id_sequences_positive` CHECK (`next_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `catalog_id_sequences` (`entity_type`, `catalog_type`, `next_id`)
SELECT 'PAGE', 'NORMAL', COALESCE(MAX(`id`), 0) + 1 FROM `catalog_pages`;

INSERT INTO `catalog_id_sequences` (`entity_type`, `catalog_type`, `next_id`)
SELECT 'OFFER', 'NORMAL', COALESCE(MAX(`id`), 0) + 1 FROM `catalog_items`;

INSERT INTO `catalog_id_sequences` (`entity_type`, `catalog_type`, `next_id`)
SELECT 'PAGE', 'BUILDER', COALESCE(MAX(`id`), 0) + 1 FROM `catalog_pages_bc`;

INSERT INTO `catalog_id_sequences` (`entity_type`, `catalog_type`, `next_id`)
SELECT 'OFFER', 'BUILDER', COALESCE(MAX(`id`), 0) + 1 FROM `catalog_items_bc`;

CREATE TABLE `catalog_change_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `actor_id` int NOT NULL,
  `summary` varchar(255) NOT NULL,
  `source` enum('UI','JSONC','SQL','RESTORE','UNDO') NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT current_timestamp(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_catalog_change_revision` (`version_id`,`revision`),
  KEY `idx_catalog_change_created` (`version_id`,`created_at`),
  CONSTRAINT `fk_catalog_change_groups_version`
    FOREIGN KEY (`version_id`) REFERENCES `catalog_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `catalog_change_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `entity_type` enum('PAGE','OFFER') NOT NULL,
  `catalog_type` enum('NORMAL','BUILDER') NOT NULL,
  `entity_id` int NOT NULL,
  `operation` enum('CREATE','UPDATE','DELETE','MOVE') NOT NULL,
  `before_json` longtext DEFAULT NULL,
  `after_json` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_catalog_change_entries_group` (`group_id`,`id`),
  KEY `idx_catalog_change_entries_entity` (`catalog_type`,`entity_type`,`entity_id`),
  CONSTRAINT `fk_catalog_change_entries_group`
    FOREIGN KEY (`group_id`) REFERENCES `catalog_change_groups` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `catalog_edit_locks` (
  `version_id` bigint NOT NULL,
  `catalog_type` enum('NORMAL','BUILDER') NOT NULL,
  `entity_type` enum('PAGE','OFFER') NOT NULL,
  `entity_id` int NOT NULL,
  `owner_id` int NOT NULL,
  `token` char(36) NOT NULL,
  `expires_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  UNIQUE KEY `uq_catalog_edit_lock` (`version_id`,`catalog_type`,`entity_type`,`entity_id`),
  KEY `idx_catalog_edit_locks_expiry` (`expires_at`),
  CONSTRAINT `fk_catalog_edit_locks_version`
    FOREIGN KEY (`version_id`) REFERENCES `catalog_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `catalog_operations` (
  `operation_id` varchar(96) NOT NULL,
  `actor_id` int NOT NULL,
  `version_id` bigint NOT NULL,
  `source` enum('UI','JSONC','SQL','RESTORE','UNDO') NOT NULL,
  `result_revision` bigint NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT current_timestamp(3),
  PRIMARY KEY (`operation_id`,`actor_id`),
  KEY `idx_catalog_operations_version` (`version_id`,`created_at`),
  CONSTRAINT `fk_catalog_operations_version`
    FOREIGN KEY (`version_id`) REFERENCES `catalog_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Capture the current live catalog as the first published version.
INSERT INTO `catalog_versions`
  (`status`, `revision`, `label`, `created_by`, `published_by`, `published_at`)
SELECT 'PUBLISHED', 0, 'Initial live catalog', 0, 0, current_timestamp(3)
WHERE NOT EXISTS (SELECT 1 FROM `catalog_versions` WHERE `status` = 'PUBLISHED');

SET @catalog_active_version_id = (
  SELECT `id` FROM `catalog_versions` WHERE `status` = 'PUBLISHED' ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `catalog_version_pages`
  (`version_id`, `catalog_type`, `page_id`, `parent_id`, `caption_save`, `caption`, `page_layout`,
   `icon_color`, `icon_image`, `min_rank`, `order_num`, `visible`, `enabled`,
   `club_only`, `catalog_mode`, `vip_only`, `page_headline`, `page_teaser`,
   `page_special`, `page_text1`, `page_text2`, `page_text_details`,
   `page_text_teaser`, `room_id`, `includes`)
SELECT @catalog_active_version_id, 'NORMAL', `id`, `parent_id`, `caption_save`, `caption`, `page_layout`,
       `icon_color`, `icon_image`, `min_rank`, `order_num`, CAST(`visible` AS UNSIGNED),
       CAST(`enabled` AS UNSIGNED), CAST(`club_only` AS UNSIGNED), `catalog_mode`,
       CAST(`vip_only` AS UNSIGNED), `page_headline`, `page_teaser`, COALESCE(`page_special`, ''),
       `page_text1`, `page_text2`, `page_text_details`, `page_text_teaser`, COALESCE(`room_id`, 0), `includes`
FROM `catalog_pages`
WHERE NOT EXISTS (
  SELECT 1 FROM `catalog_version_pages`
  WHERE `version_id` = @catalog_active_version_id AND `catalog_type` = 'NORMAL'
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
WHERE NOT EXISTS (
  SELECT 1 FROM `catalog_version_pages`
  WHERE `version_id` = @catalog_active_version_id AND `catalog_type` = 'BUILDER'
);

INSERT INTO `catalog_version_offers`
  (`version_id`, `catalog_type`, `offer_id`, `item_ids`, `page_id`, `catalog_name`, `cost_credits`,
   `cost_points`, `points_type`, `amount`, `limited_stack`, `order_number`,
   `offer_id_client`, `song_id`, `extradata`, `have_offer`, `club_only`)
SELECT @catalog_active_version_id, 'NORMAL', `id`, `item_ids`, `page_id`, `catalog_name`, `cost_credits`,
       `cost_points`, `points_type`, `amount`, `limited_stack`, `order_number`,
       `offer_id`, `song_id`, `extradata`, CAST(`have_offer` AS UNSIGNED), CAST(`club_only` AS UNSIGNED)
FROM `catalog_items`
WHERE NOT EXISTS (
  SELECT 1 FROM `catalog_version_offers`
  WHERE `version_id` = @catalog_active_version_id AND `catalog_type` = 'NORMAL'
);

INSERT INTO `catalog_version_offers`
  (`version_id`, `catalog_type`, `offer_id`, `item_ids`, `page_id`, `catalog_name`, `cost_credits`,
   `cost_points`, `points_type`, `amount`, `limited_stack`, `order_number`,
   `offer_id_client`, `song_id`, `extradata`, `have_offer`, `club_only`)
SELECT @catalog_active_version_id, 'BUILDER', `id`, `item_ids`, `page_id`, `catalog_name`, 0,
       0, 0, 1, 0, `order_number`, -1, 0, `extradata`, 1, 0
FROM `catalog_items_bc`
WHERE NOT EXISTS (
  SELECT 1 FROM `catalog_version_offers`
  WHERE `version_id` = @catalog_active_version_id AND `catalog_type` = 'BUILDER'
);

-- Create the shared draft from the active snapshot.
INSERT INTO `catalog_versions`
  (`status`, `based_on_version_id`, `revision`, `label`, `created_by`)
SELECT 'DRAFT', @catalog_active_version_id, 0, 'Shared catalog draft', 0
WHERE NOT EXISTS (SELECT 1 FROM `catalog_versions` WHERE `status` = 'DRAFT');

SET @catalog_draft_version_id = (
  SELECT `id` FROM `catalog_versions` WHERE `status` = 'DRAFT' ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `catalog_version_pages`
SELECT @catalog_draft_version_id, `catalog_type`, `page_id`, `parent_id`, `caption_save`, `caption`, `page_layout`,
       `icon_color`, `icon_image`, `min_rank`, `order_num`, `visible`, `enabled`, `club_only`,
       `catalog_mode`, `vip_only`, `page_headline`, `page_teaser`, `page_special`, `page_text1`,
       `page_text2`, `page_text_details`, `page_text_teaser`, `room_id`, `includes`
FROM `catalog_version_pages`
WHERE `version_id` = @catalog_active_version_id
  AND NOT EXISTS (
    SELECT 1 FROM `catalog_version_pages` WHERE `version_id` = @catalog_draft_version_id
  );

INSERT INTO `catalog_version_offers`
SELECT @catalog_draft_version_id, `catalog_type`, `offer_id`, `item_ids`, `page_id`, `catalog_name`,
       `cost_credits`, `cost_points`, `points_type`, `amount`, `limited_stack`, `order_number`,
       `offer_id_client`, `song_id`, `extradata`, `have_offer`, `club_only`
FROM `catalog_version_offers`
WHERE `version_id` = @catalog_active_version_id
  AND NOT EXISTS (
    SELECT 1 FROM `catalog_version_offers` WHERE `version_id` = @catalog_draft_version_id
  );

INSERT INTO `catalog_runtime_state`
  (`singleton_id`, `active_version_id`, `draft_version_id`)
SELECT 1, @catalog_active_version_id, @catalog_draft_version_id
WHERE NOT EXISTS (SELECT 1 FROM `catalog_runtime_state` WHERE `singleton_id` = 1);
