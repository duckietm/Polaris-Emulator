-- The up-counter family never reached the interaction repair of 2026-08-22.
--
-- That migration reconciled `wf_game_upcounter1` and `wf_game_upcounter2` onto
-- `game_upcounter`, but it never listed `wf_upcounter1` and `wf_upcounter2`, so
-- both kept whatever the historical baseline gave them. `wf_upcounter2` was left
-- on `counter`, which on every hotel is the ordinary kitchen-counter furniture
-- shared by a hundred decorative items and backed by no interaction class at
-- all. The furni is the Hourglass Counter, sibling of the Small Wired Counter:
-- it is sold as a wired timer, it falls through to InteractionDefault, and it
-- counts nothing. Nothing reports the mismatch, because an unregistered
-- interaction type is not an error - ItemManager.getItemInteraction returns the
-- default silently.
--
-- `wf_upcounter1` is already correct on a current baseline and is listed here
-- only so the whole family converges on adopted databases that carry an older
-- value. The update touches a row solely when it diverges, so a hotel that
-- already holds the right value is left untouched.
--
-- Only `interaction_type` is reconciled. `public_name` is operator-visible and
-- routinely customised, so it stays as the hotel has it.
--
-- Charset handling follows V20260822164532: adopted hotels carry latin1,
-- utf8mb4_general_ci or utf8mb4_unicode_ci on `items_base`, and a bare
-- column-to-column comparison against a fixed-charset lookup table raises
-- "Illegal mix of collations". Every comparison therefore converts the
-- `items_base` side and pins the collation explicitly. The identifiers are
-- ASCII furnidata classnames, so the conversion never changes what matches.

CREATE TEMPORARY TABLE `polaris_items_base_upcounter_repair_20260905` (
    `item_identifier` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (`item_identifier`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_upcounter_repair_20260905`
    (`item_identifier`, `interaction_type`)
VALUES
    ('wf_upcounter1', 'game_upcounter'),
    ('wf_upcounter2', 'game_upcounter');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_upcounter_repair_20260905` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`
WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci
    <> mapping.`interaction_type`;

-- Older dumps identify the same furni by `public_name`, the way the 2026-08-22
-- repair did. Match those too, but never when `item_name` already named the row:
-- the classname is the stable identity and must win.
UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_upcounter_repair_20260905` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`public_name` USING utf8mb4) COLLATE utf8mb4_general_ci
LEFT JOIN `polaris_items_base_upcounter_repair_20260905` AS item_name_mapping
    ON item_name_mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`
WHERE item_name_mapping.`item_identifier` IS NULL
  AND CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci
      <> mapping.`interaction_type`;

DROP TEMPORARY TABLE `polaris_items_base_upcounter_repair_20260905`;
