-- Some imported catalog snapshots contain the ordinal positions of legacy ENUM('0','1')
-- columns. In that encoding 1 means label '0' and 2 means label '1'. JDBC getBoolean
-- treats both ordinals as true, which can publish every page and offer as club-only.

CREATE TEMPORARY TABLE `catalog_page_boolean_baseline` AS
SELECT
  `source`.`version_id`,
  `source`.`catalog_type`,
  `source`.`page_id`,
  `source`.`visible`,
  `source`.`enabled`,
  `source`.`club_only`,
  `source`.`vip_only`
FROM `catalog_version_pages` `source`
JOIN (
  SELECT
    `catalog_type`,
    `page_id`,
    MIN(`version_id`) AS `version_id`
  FROM `catalog_version_pages`
  WHERE `visible` = 2
     OR `enabled` = 2
     OR `club_only` = 2
     OR `vip_only` = 2
  GROUP BY `catalog_type`, `page_id`
) `first_ordinal`
  ON `first_ordinal`.`version_id` = `source`.`version_id`
 AND `first_ordinal`.`catalog_type` = `source`.`catalog_type`
 AND `first_ordinal`.`page_id` = `source`.`page_id`;

ALTER TABLE `catalog_page_boolean_baseline`
  ADD PRIMARY KEY (`catalog_type`, `page_id`);

UPDATE `catalog_version_pages` `page`
JOIN `catalog_page_boolean_baseline` `baseline`
  ON `baseline`.`catalog_type` = `page`.`catalog_type`
 AND `baseline`.`page_id` = `page`.`page_id`
 AND `page`.`version_id` >= `baseline`.`version_id`
SET
  `page`.`visible` = IF(`baseline`.`visible` = 2, 1, 0),
  `page`.`enabled` = IF(`baseline`.`enabled` = 2, 1, 0),
  `page`.`club_only` = IF(`baseline`.`club_only` = 2, 1, 0),
  -- catalog_pages.vip_only uses the reversed legacy enum('1','0') declaration.
  `page`.`vip_only` = IF(`baseline`.`vip_only` = 1, 1, 0);

CREATE TEMPORARY TABLE `catalog_offer_boolean_baseline` AS
SELECT
  `source`.`version_id`,
  `source`.`catalog_type`,
  `source`.`offer_id`,
  `source`.`have_offer`,
  `source`.`club_only`
FROM `catalog_version_offers` `source`
JOIN (
  SELECT
    `catalog_type`,
    `offer_id`,
    MIN(`version_id`) AS `version_id`
  FROM `catalog_version_offers`
  WHERE `have_offer` = 2
     OR `club_only` = 2
  GROUP BY `catalog_type`, `offer_id`
) `first_ordinal`
  ON `first_ordinal`.`version_id` = `source`.`version_id`
 AND `first_ordinal`.`catalog_type` = `source`.`catalog_type`
 AND `first_ordinal`.`offer_id` = `source`.`offer_id`;

ALTER TABLE `catalog_offer_boolean_baseline`
  ADD PRIMARY KEY (`catalog_type`, `offer_id`);

UPDATE `catalog_version_offers` `offer`
JOIN `catalog_offer_boolean_baseline` `baseline`
  ON `baseline`.`catalog_type` = `offer`.`catalog_type`
 AND `baseline`.`offer_id` = `offer`.`offer_id`
 AND `offer`.`version_id` >= `baseline`.`version_id`
SET
  `offer`.`have_offer` = IF(`baseline`.`have_offer` = 2, 1, 0),
  `offer`.`club_only` = IF(`baseline`.`club_only` = 2, 1, 0);

-- Normalize any remaining non-ordinal values and prevent a future silent reintroduction.
UPDATE `catalog_version_pages`
SET
  `visible` = IF(`visible` <> 0, 1, 0),
  `enabled` = IF(`enabled` <> 0, 1, 0),
  `club_only` = IF(`club_only` <> 0, 1, 0),
  `vip_only` = IF(`vip_only` <> 0, 1, 0);

UPDATE `catalog_version_offers`
SET
  `have_offer` = IF(`have_offer` <> 0, 1, 0),
  `club_only` = IF(`club_only` <> 0, 1, 0);

ALTER TABLE `catalog_version_pages`
  ADD CONSTRAINT `chk_catalog_version_page_booleans`
    CHECK (`visible` IN (0, 1) AND `enabled` IN (0, 1) AND `club_only` IN (0, 1) AND `vip_only` IN (0, 1));

ALTER TABLE `catalog_version_offers`
  ADD CONSTRAINT `chk_catalog_version_offer_booleans`
    CHECK (`have_offer` IN (0, 1) AND `club_only` IN (0, 1));

-- Restore the operational tables from the repaired active snapshot. Quoted labels are
-- required because numeric assignments target the ordinal positions of ENUM columns.
UPDATE `catalog_pages` `live`
JOIN `catalog_runtime_state` `runtime` ON `runtime`.`singleton_id` = 1
JOIN `catalog_version_pages` `page`
  ON `page`.`version_id` = `runtime`.`active_version_id`
 AND `page`.`catalog_type` = 'NORMAL'
 AND `page`.`page_id` = `live`.`id`
SET
  `live`.`visible` = IF(`page`.`visible` = 1, '1', '0'),
  `live`.`enabled` = IF(`page`.`enabled` = 1, '1', '0'),
  `live`.`club_only` = IF(`page`.`club_only` = 1, '1', '0'),
  `live`.`vip_only` = IF(`page`.`vip_only` = 1, '1', '0');

UPDATE `catalog_items` `live`
JOIN `catalog_runtime_state` `runtime` ON `runtime`.`singleton_id` = 1
JOIN `catalog_version_offers` `offer`
  ON `offer`.`version_id` = `runtime`.`active_version_id`
 AND `offer`.`catalog_type` = 'NORMAL'
 AND `offer`.`offer_id` = `live`.`id`
SET
  `live`.`have_offer` = IF(`offer`.`have_offer` = 1, '1', '0'),
  `live`.`club_only` = IF(`offer`.`club_only` = 1, '1', '0');

UPDATE `catalog_pages_bc` `live`
JOIN `catalog_runtime_state` `runtime` ON `runtime`.`singleton_id` = 1
JOIN `catalog_version_pages` `page`
  ON `page`.`version_id` = `runtime`.`active_version_id`
 AND `page`.`catalog_type` = 'BUILDER'
 AND `page`.`page_id` = `live`.`id`
SET
  `live`.`visible` = IF(`page`.`visible` = 1, '1', '0'),
  `live`.`enabled` = IF(`page`.`enabled` = 1, '1', '0');

DROP TEMPORARY TABLE `catalog_offer_boolean_baseline`;
DROP TEMPORARY TABLE `catalog_page_boolean_baseline`;
