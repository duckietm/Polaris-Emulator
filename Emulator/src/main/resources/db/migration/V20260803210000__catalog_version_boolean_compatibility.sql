-- Repair catalog booleans imported from legacy enum('0','1') columns.
-- MariaDB casts enum labels to their ordinal positions, so the initial snapshot stored 1/2 instead of 0/1.

CREATE TEMPORARY TABLE `catalog_recovered_page_booleans` AS
SELECT
  `page`.`catalog_type`,
  `page`.`page_id`,
  CASE
    WHEN `encoding`.`uses_enum_ordinals` = 1 THEN IF(`page`.`visible` = 2, 1, 0)
    ELSE IF(`page`.`visible` <> 0, 1, 0)
  END AS `visible`,
  CASE
    WHEN `encoding`.`uses_enum_ordinals` = 1 THEN IF(`page`.`enabled` = 2, 1, 0)
    ELSE IF(`page`.`enabled` <> 0, 1, 0)
  END AS `enabled`,
  CASE
    WHEN `encoding`.`uses_enum_ordinals` = 1 THEN IF(`page`.`club_only` = 2, 1, 0)
    ELSE IF(`page`.`club_only` <> 0, 1, 0)
  END AS `club_only`,
  CASE
    -- catalog_pages.vip_only uses the reversed enum('1','0') declaration.
    WHEN `encoding`.`uses_enum_ordinals` = 1 THEN IF(`page`.`vip_only` = 1, 1, 0)
    ELSE IF(`page`.`vip_only` <> 0, 1, 0)
  END AS `vip_only`
FROM `catalog_version_pages` `page`
JOIN (
  SELECT MIN(`id`) AS `version_id`
  FROM `catalog_versions`
  WHERE `based_on_version_id` IS NULL
) `root` ON `root`.`version_id` = `page`.`version_id`
CROSS JOIN (
  SELECT IF(COUNT(*) > 0, 1, 0) AS `uses_enum_ordinals`
  FROM `catalog_version_pages` `candidate`
  JOIN (
    SELECT MIN(`id`) AS `version_id`
    FROM `catalog_versions`
    WHERE `based_on_version_id` IS NULL
  ) `initial` ON `initial`.`version_id` = `candidate`.`version_id`
  WHERE `candidate`.`visible` = 2
     OR `candidate`.`enabled` = 2
     OR `candidate`.`club_only` = 2
     OR `candidate`.`vip_only` = 2
) `encoding`;

ALTER TABLE `catalog_recovered_page_booleans`
  ADD PRIMARY KEY (`catalog_type`, `page_id`);

UPDATE `catalog_version_pages` `page`
LEFT JOIN `catalog_recovered_page_booleans` `recovered`
  ON `recovered`.`catalog_type` = `page`.`catalog_type`
 AND `recovered`.`page_id` = `page`.`page_id`
SET
  `page`.`visible` = COALESCE(`recovered`.`visible`, IF(`page`.`visible` <> 0, 1, 0)),
  `page`.`enabled` = COALESCE(`recovered`.`enabled`, IF(`page`.`enabled` <> 0, 1, 0)),
  `page`.`club_only` = COALESCE(`recovered`.`club_only`, IF(`page`.`club_only` <> 0, 1, 0)),
  `page`.`vip_only` = COALESCE(`recovered`.`vip_only`, IF(`page`.`vip_only` <> 0, 1, 0));

CREATE TEMPORARY TABLE `catalog_recovered_offer_booleans` AS
SELECT
  `offer`.`catalog_type`,
  `offer`.`offer_id`,
  CASE
    WHEN `encoding`.`uses_enum_ordinals` = 1 THEN IF(`offer`.`have_offer` = 2, 1, 0)
    ELSE IF(`offer`.`have_offer` <> 0, 1, 0)
  END AS `have_offer`,
  CASE
    WHEN `encoding`.`uses_enum_ordinals` = 1 THEN IF(`offer`.`club_only` = 2, 1, 0)
    ELSE IF(`offer`.`club_only` <> 0, 1, 0)
  END AS `club_only`
FROM `catalog_version_offers` `offer`
JOIN (
  SELECT MIN(`id`) AS `version_id`
  FROM `catalog_versions`
  WHERE `based_on_version_id` IS NULL
) `root` ON `root`.`version_id` = `offer`.`version_id`
CROSS JOIN (
  SELECT IF(COUNT(*) > 0, 1, 0) AS `uses_enum_ordinals`
  FROM `catalog_version_offers` `candidate`
  JOIN (
    SELECT MIN(`id`) AS `version_id`
    FROM `catalog_versions`
    WHERE `based_on_version_id` IS NULL
  ) `initial` ON `initial`.`version_id` = `candidate`.`version_id`
  WHERE `candidate`.`have_offer` = 2
     OR `candidate`.`club_only` = 2
) `encoding`;

ALTER TABLE `catalog_recovered_offer_booleans`
  ADD PRIMARY KEY (`catalog_type`, `offer_id`);

UPDATE `catalog_version_offers` `offer`
LEFT JOIN `catalog_recovered_offer_booleans` `recovered`
  ON `recovered`.`catalog_type` = `offer`.`catalog_type`
 AND `recovered`.`offer_id` = `offer`.`offer_id`
SET
  `offer`.`have_offer` = COALESCE(`recovered`.`have_offer`, IF(`offer`.`have_offer` <> 0, 1, 0)),
  `offer`.`club_only` = COALESCE(`recovered`.`club_only`, IF(`offer`.`club_only` <> 0, 1, 0));

-- Restore the operational catalog from the active, normalized snapshot.
-- Quoted labels are required here: numeric values address enum positions instead of enum labels.
UPDATE `catalog_pages` `live`
JOIN `catalog_runtime_state` `runtime` ON `runtime`.`singleton_id` = 1
JOIN `catalog_version_pages` `page`
  ON `page`.`version_id` = `runtime`.`active_version_id`
 AND `page`.`catalog_type` = 'NORMAL'
 AND `page`.`page_id` = `live`.`id`
SET
  `live`.`visible` = IF(`page`.`visible` <> 0, '1', '0'),
  `live`.`enabled` = IF(`page`.`enabled` <> 0, '1', '0'),
  `live`.`club_only` = IF(`page`.`club_only` <> 0, '1', '0'),
  `live`.`vip_only` = IF(`page`.`vip_only` <> 0, '1', '0');

UPDATE `catalog_items` `live`
JOIN `catalog_runtime_state` `runtime` ON `runtime`.`singleton_id` = 1
JOIN `catalog_version_offers` `offer`
  ON `offer`.`version_id` = `runtime`.`active_version_id`
 AND `offer`.`catalog_type` = 'NORMAL'
 AND `offer`.`offer_id` = `live`.`id`
SET
  `live`.`have_offer` = IF(`offer`.`have_offer` <> 0, '1', '0'),
  `live`.`club_only` = IF(`offer`.`club_only` <> 0, '1', '0');

UPDATE `catalog_pages_bc` `live`
JOIN `catalog_runtime_state` `runtime` ON `runtime`.`singleton_id` = 1
JOIN `catalog_version_pages` `page`
  ON `page`.`version_id` = `runtime`.`active_version_id`
 AND `page`.`catalog_type` = 'BUILDER'
 AND `page`.`page_id` = `live`.`id`
SET
  `live`.`visible` = IF(`page`.`visible` <> 0, '1', '0'),
  `live`.`enabled` = IF(`page`.`enabled` <> 0, '1', '0');

DROP TEMPORARY TABLE `catalog_recovered_offer_booleans`;
DROP TEMPORARY TABLE `catalog_recovered_page_booleans`;
