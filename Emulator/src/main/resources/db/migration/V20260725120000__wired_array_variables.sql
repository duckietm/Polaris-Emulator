-- Polaris Wired arrays. Definitions remain on the four existing variable boxes;
-- only permanent owner values are stored here. Context arrays stay execution-scoped.

CREATE TABLE IF NOT EXISTS `room_wired_array_values` (
    `room_id` INT NOT NULL,
    `variable_item_id` INT NOT NULL,
    `owner_type` TINYINT NOT NULL,
    `owner_id` INT NOT NULL,
    `logical_length` INT NOT NULL DEFAULT 0,
    `version` BIGINT NOT NULL DEFAULT 0,
    `created_at` INT NOT NULL DEFAULT 0,
    `updated_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`, `variable_item_id`, `owner_type`, `owner_id`),
    KEY `idx_room_wired_array_definition` (`room_id`, `variable_item_id`),
    KEY `idx_room_wired_array_owner` (`room_id`, `owner_type`, `owner_id`),
    CONSTRAINT `chk_room_wired_array_owner_type`
        CHECK (`owner_type` BETWEEN 0 AND 2),
    CONSTRAINT `chk_room_wired_array_length`
        CHECK (`logical_length` BETWEEN 0 AND 2048),
    CONSTRAINT `chk_room_wired_array_version`
        CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_wired_array_entries` (
    `room_id` INT NOT NULL,
    `variable_item_id` INT NOT NULL,
    `owner_type` TINYINT NOT NULL,
    `owner_id` INT NOT NULL,
    `entry_index` INT NOT NULL,
    `entry_data` JSON NOT NULL,
    PRIMARY KEY (`room_id`, `variable_item_id`, `owner_type`, `owner_id`, `entry_index`),
    CONSTRAINT `fk_room_wired_array_entry_value`
        FOREIGN KEY (`room_id`, `variable_item_id`, `owner_type`, `owner_id`)
        REFERENCES `room_wired_array_values`
            (`room_id`, `variable_item_id`, `owner_type`, `owner_id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `chk_room_wired_array_entry_index`
        CHECK (`entry_index` BETWEEN 0 AND 2047),
    CONSTRAINT `chk_room_wired_array_entry_json`
        CHECK (JSON_VALID(`entry_data`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.wired.arrays.max_entries', '2048', 'Maximum logical capacity allowed for a Wired array definition.'),
    ('hotel.wired.arrays.max_populated_cells_per_owner', '4096', 'Maximum stored array fields per owner and variable.'),
    ('hotel.wired.arrays.max_owners_per_execution', '50', 'Maximum array owners processed by one Wired box execution.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Fixed sprite IDs match FurnitureData; database row IDs remain installation-local.
INSERT INTO `items_base`
    (`sprite_id`, `public_name`, `item_name`, `type`, `width`, `length`, `stack_height`,
     `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`, `allow_gift`, `allow_trade`,
     `allow_recycle`, `allow_marketplace_sell`, `allow_inventory_stack`, `interaction_type`,
     `interaction_modes_count`, `vending_ids`, `multiheight`, `customparams`)
SELECT 2000029849, 'WIRED Effect: Modify Array', 'wf_act_modify_array', 's', 1, 1, 0.65,
       1, 0, 0, 1, 1, 1, 0, 0, 1, 'wf_act_modify_array', 2, '0', '', ''
WHERE NOT EXISTS (
    SELECT 1 FROM `items_base` WHERE `item_name` = 'wf_act_modify_array'
);

INSERT INTO `items_base`
    (`sprite_id`, `public_name`, `item_name`, `type`, `width`, `length`, `stack_height`,
     `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`, `allow_gift`, `allow_trade`,
     `allow_recycle`, `allow_marketplace_sell`, `allow_inventory_stack`, `interaction_type`,
     `interaction_modes_count`, `vending_ids`, `multiheight`, `customparams`)
SELECT 2000029850, 'WIRED Condition: Check Array', 'wf_cnd_check_array', 's', 1, 1, 0.65,
       1, 0, 0, 0, 1, 1, 0, 0, 1, 'wf_cnd_check_array', 2, '0', '', ''
WHERE NOT EXISTS (
    SELECT 1 FROM `items_base` WHERE `item_name` = 'wf_cnd_check_array'
);

INSERT INTO `items_base`
    (`sprite_id`, `public_name`, `item_name`, `type`, `width`, `length`, `stack_height`,
     `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`, `allow_gift`, `allow_trade`,
     `allow_recycle`, `allow_marketplace_sell`, `allow_inventory_stack`, `interaction_type`,
     `interaction_modes_count`, `vending_ids`, `multiheight`, `customparams`)
SELECT 2000029851, 'WIRED Add-on: Array Capturer', 'wf_xtra_array_capture_variable', 's', 1, 1, 0.65,
       1, 0, 0, 1, 1, 1, 0, 0, 1, 'wf_xtra_array_capture_variable', 2, '0', '', ''
WHERE NOT EXISTS (
    SELECT 1 FROM `items_base` WHERE `item_name` = 'wf_xtra_array_capture_variable'
);

INSERT INTO `catalog_items`
    (`item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`,
     `amount`, `order_number`, `offer_id`, `extradata`, `have_offer`, `club_only`)
SELECT CAST(base.`id` AS CHAR), page.`id`, base.`item_name`, 5, 0, 0, 1,
       COALESCE((SELECT MAX(existing.`order_number`) + 1 FROM `catalog_items` existing
                 WHERE existing.`page_id` = page.`id`), 1),
       -1, '', '1', '0'
FROM `items_base` base
CROSS JOIN (
    SELECT `id`
    FROM `catalog_pages`
    WHERE `caption_save` = 'effects' OR LOWER(`caption`) = 'effects'
    ORDER BY CASE WHEN `caption_save` = 'effects' THEN 0 ELSE 1 END, `id`
    LIMIT 1
) page
WHERE base.`item_name` = 'wf_act_modify_array'
  AND NOT EXISTS (
      SELECT 1 FROM `catalog_items` existing
      WHERE existing.`catalog_name` = 'wf_act_modify_array'
        AND existing.`page_id` = page.`id`
  );

INSERT INTO `catalog_items`
    (`item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`,
     `amount`, `order_number`, `offer_id`, `extradata`, `have_offer`, `club_only`)
SELECT CAST(base.`id` AS CHAR), page.`id`, base.`item_name`, 5, 0, 0, 1,
       COALESCE((SELECT MAX(existing.`order_number`) + 1 FROM `catalog_items` existing
                 WHERE existing.`page_id` = page.`id`), 1),
       -1, '', '1', '0'
FROM `items_base` base
CROSS JOIN (
    SELECT `id`
    FROM `catalog_pages`
    WHERE `caption_save` = 'conditions' OR LOWER(`caption`) = 'conditions'
    ORDER BY CASE WHEN `caption_save` = 'conditions' THEN 0 ELSE 1 END, `id`
    LIMIT 1
) page
WHERE base.`item_name` = 'wf_cnd_check_array'
  AND NOT EXISTS (
      SELECT 1 FROM `catalog_items` existing
      WHERE existing.`catalog_name` = 'wf_cnd_check_array'
        AND existing.`page_id` = page.`id`
  );

INSERT INTO `catalog_items`
    (`item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`,
     `amount`, `order_number`, `offer_id`, `extradata`, `have_offer`, `club_only`)
SELECT CAST(base.`id` AS CHAR), page.`id`, base.`item_name`, 5, 0, 0, 1,
       COALESCE((SELECT MAX(existing.`order_number`) + 1 FROM `catalog_items` existing
                 WHERE existing.`page_id` = page.`id`), 1),
       -1, '', '1', '0'
FROM `items_base` base
CROSS JOIN (
    SELECT `id`
    FROM `catalog_pages`
    WHERE LOWER(REPLACE(REPLACE(`caption_save`, '-', ''), ' ', '')) = 'addons'
       OR LOWER(REPLACE(REPLACE(`caption`, '-', ''), ' ', '')) = 'addons'
    ORDER BY CASE WHEN `caption_save` = 'add-ons' THEN 0 ELSE 1 END, `id`
    LIMIT 1
) page
WHERE base.`item_name` = 'wf_xtra_array_capture_variable'
  AND NOT EXISTS (
      SELECT 1 FROM `catalog_items` existing
      WHERE existing.`catalog_name` = 'wf_xtra_array_capture_variable'
        AND existing.`page_id` = page.`id`
  );
