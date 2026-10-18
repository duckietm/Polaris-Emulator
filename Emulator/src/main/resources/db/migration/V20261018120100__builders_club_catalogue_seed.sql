-- Seeds the Builders Club catalogue from the hotel's own furni catalogue, as Habbo's Builders Club
-- mode mirrors the shop: every page that sells plain single furni gets a Builders Club twin (with its
-- parents, captions, icons and texts) holding one free offer per furni.
--
-- Only an untouched Builders Club catalogue is seeded: no offers yet and no pages besides the two
-- defaults. A hotel that already built its own keeps it as it is.
--
-- Left out: limited, club-only and multi-item offers, hidden, disabled or staff pages, and furni whose
-- behaviour depends on owning them (pets, teleports, trophies, crackables, clothing, wired, songs,
-- rentable spaces and the like).

SET @bc_seed := (SELECT COUNT(*) FROM `catalog_items_bc`) = 0
            AND (SELECT COUNT(*) FROM `catalog_pages_bc` WHERE `id` > 2) = 0;

DROP TABLE IF EXISTS `bc_seed_offers`;
DROP TABLE IF EXISTS `bc_seed_leaf_pages`;
DROP TABLE IF EXISTS `bc_seed_pages`;
DROP TABLE IF EXISTS `bc_seed_page_map`;

CREATE TABLE `bc_seed_offers` (
  `item_id` int(11) NOT NULL PRIMARY KEY,
  `catalog_item_id` int(11) NOT NULL,
  `page_id` int(11) NOT NULL,
  `catalog_name` varchar(100) NOT NULL,
  `order_number` int(11) NOT NULL,
  `extradata` varchar(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One offer per furni: the first one met in page order wins.
INSERT IGNORE INTO `bc_seed_offers` (`item_id`, `catalog_item_id`, `page_id`, `catalog_name`, `order_number`, `extradata`)
SELECT CAST(ci.`item_ids` AS UNSIGNED), ci.`id`, ci.`page_id`, ci.`catalog_name`, ci.`order_number`, ci.`extradata`
FROM `catalog_items` ci
INNER JOIN `catalog_pages` cp ON cp.`id` = ci.`page_id`
INNER JOIN `items_base` ib ON ib.`id` = CAST(ci.`item_ids` AS UNSIGNED)
WHERE @bc_seed
  AND ci.`item_ids` REGEXP '^[0-9]+$'
  AND ci.`amount` = 1
  AND ci.`limited_stack` = 0
  AND ci.`club_only` = '0'
  AND ci.`have_offer` = '1'
  AND cp.`enabled` = '1'
  AND cp.`visible` = '1'
  AND cp.`min_rank` <= 1
  AND cp.`club_only` = '0'
  AND cp.`vip_only` = '0'
  AND cp.`catalog_mode` IN ('NORMAL', 'BOTH')
  AND cp.`page_layout` IN ('default_3x3', 'default_3x3_color_grouping')
  AND ib.`type` IN ('s', 'i')
  AND ib.`item_name` NOT LIKE 'clothing\_%'
  AND ib.`item_name` NOT LIKE 'fxbox\_%'
  AND ib.`interaction_type` NOT LIKE 'wf\_%'
  AND ib.`interaction_type` NOT LIKE 'pet%'
  AND ib.`interaction_type` NOT LIKE 'crackable%'
  AND ib.`interaction_type` NOT LIKE 'teleport%'
  AND ib.`interaction_type` NOT LIKE 'guild\_%'
  AND ib.`interaction_type` NOT IN (
      'clothing', 'nest', 'breeding_nest', 'trophy', 'badge_display', 'love_lock', 'fx_box', 'gift',
      'rentable_space', 'mannequin', 'postit', 'monsterplant_seed', 'trax_machine', 'jukebox', 'crafting',
      'club_gate', 'vip_gate', 'costume_hoppper', 'puzzle_box', 'effect_giver', 'external_image',
      'information_terminal', 'roomeffect', 'gnome_box', 'mysterybox', 'mystery_box', 'random_box')
ORDER BY cp.`order_num`, cp.`id`, ci.`order_number`, ci.`id`;

CREATE TABLE `bc_seed_leaf_pages` (
  `page_id` int(11) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO `bc_seed_leaf_pages` (`page_id`)
SELECT DISTINCT `page_id` FROM `bc_seed_offers`;

-- The pages that sell them and every parent up to the root.
CREATE TABLE `bc_seed_pages` (
  `page_id` int(11) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT IGNORE INTO `bc_seed_pages` (`page_id`)
WITH RECURSIVE `ancestors` AS (
    SELECT cp.`id`, cp.`parent_id`
    FROM `catalog_pages` cp
    INNER JOIN `bc_seed_leaf_pages` leaf ON leaf.`page_id` = cp.`id`
    UNION
    SELECT parent.`id`, parent.`parent_id`
    FROM `catalog_pages` parent
    INNER JOIN `ancestors` child ON parent.`id` = child.`parent_id`
)
SELECT `id` FROM `ancestors`;

-- Builders Club page ids, given root first so a parent always exists before its children. A page under
-- a hidden, staff or club parent, or one that does not reach a root, is left out. Folder pages keep
-- their enabled flag, so a heading without a page of its own stays a heading.
CREATE TABLE `bc_seed_page_map` (
  `page_id` int(11) NOT NULL PRIMARY KEY,
  `bc_page_id` int(11) NOT NULL UNIQUE,
  `depth` int(11) NOT NULL
) ENGINE=InnoDB;

SET @bc_page_base := GREATEST(
    COALESCE((SELECT MAX(`id`) FROM `catalog_pages_bc`), 0),
    COALESCE((SELECT `next_id` - 1 FROM `catalog_id_sequences` WHERE `entity_type` = 'PAGE' AND `catalog_type` = 'BUILDER'), 0));

INSERT INTO `bc_seed_page_map` (`page_id`, `bc_page_id`, `depth`)
WITH RECURSIVE `tree` AS (
    SELECT cp.`id`, cp.`order_num`, 0 AS `depth`
    FROM `catalog_pages` cp
    INNER JOIN `bc_seed_pages` needed ON needed.`page_id` = cp.`id`
    WHERE cp.`parent_id` = -1
      AND cp.`visible` = '1' AND cp.`min_rank` <= 1 AND cp.`club_only` = '0' AND cp.`vip_only` = '0'
    UNION ALL
    SELECT cp.`id`, cp.`order_num`, tree.`depth` + 1
    FROM `catalog_pages` cp
    INNER JOIN `tree` ON cp.`parent_id` = tree.`id`
    WHERE cp.`id` IN (SELECT `page_id` FROM `bc_seed_pages`)
      AND cp.`visible` = '1' AND cp.`min_rank` <= 1 AND cp.`club_only` = '0' AND cp.`vip_only` = '0'
)
SELECT `id`, @bc_page_base + ROW_NUMBER() OVER (ORDER BY `depth`, `order_num`, `id`), `depth`
FROM `tree`;

INSERT INTO `catalog_pages_bc`
  (`id`, `parent_id`, `caption`, `page_layout`, `icon_color`, `icon_image`, `order_num`, `visible`, `enabled`,
   `page_headline`, `page_teaser`, `page_special`, `page_text1`, `page_text2`, `page_text_details`, `page_text_teaser`)
SELECT map.`bc_page_id`,
       COALESCE(parent_map.`bc_page_id`, -1),
       cp.`caption`,
       IF(cp.`page_layout` = 'default_3x3_color_grouping', 'default_3x3_color_grouping', 'default_3x3'),
       cp.`icon_color`,
       cp.`icon_image`,
       cp.`order_num`,
       '1',
       cp.`enabled`,
       cp.`page_headline`,
       cp.`page_teaser`,
       COALESCE(cp.`page_special`, ''),
       cp.`page_text1`,
       cp.`page_text2`,
       cp.`page_text_details`,
       cp.`page_text_teaser`
FROM `bc_seed_page_map` map
INNER JOIN `catalog_pages` cp ON cp.`id` = map.`page_id`
LEFT JOIN `bc_seed_page_map` parent_map ON parent_map.`page_id` = cp.`parent_id`
ORDER BY map.`depth`, map.`bc_page_id`;

INSERT INTO `catalog_items_bc` (`item_ids`, `page_id`, `catalog_name`, `order_number`, `extradata`)
SELECT CAST(offer.`item_id` AS CHAR), map.`bc_page_id`, offer.`catalog_name`, offer.`order_number`, offer.`extradata`
FROM `bc_seed_offers` offer
INNER JOIN `bc_seed_page_map` map ON map.`page_id` = offer.`page_id`
ORDER BY map.`bc_page_id`, offer.`order_number`, offer.`catalog_item_id`;

-- The empty default root would sit next to the seeded tree as a blank tab.
UPDATE `catalog_pages_bc`
SET `visible` = '0'
WHERE @bc_seed
  AND `id` = 1
  AND EXISTS (SELECT 1 FROM `bc_seed_page_map`)
  AND NOT EXISTS (SELECT 1 FROM `catalog_items_bc` WHERE `page_id` = 1);

-- Keep the Catalog Manager's id sequences ahead of the seeded rows.
UPDATE `catalog_id_sequences`
SET `next_id` = GREATEST(`next_id`, (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `catalog_pages_bc`))
WHERE `entity_type` = 'PAGE' AND `catalog_type` = 'BUILDER';

UPDATE `catalog_id_sequences`
SET `next_id` = GREATEST(`next_id`, (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `catalog_items_bc`))
WHERE `entity_type` = 'OFFER' AND `catalog_type` = 'BUILDER';

DROP TABLE `bc_seed_offers`;
DROP TABLE `bc_seed_leaf_pages`;
DROP TABLE `bc_seed_pages`;
DROP TABLE `bc_seed_page_map`;
