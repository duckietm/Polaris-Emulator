-- Furni state counts that differ from Habbo's: how many states a furni steps through when it is used or toggled
-- by wired. A gate with one state could not open at all.
--
-- Each row is only touched while it still has the count and the interaction this hotel shipped with, so a class
-- a hotel has set up differently is left alone. Where the count goes down, placed furni standing in a state that
-- no longer exists move to the last one. Idempotent: a rerun changes nothing.

UPDATE `items_base` SET `interaction_modes_count` = 2
    WHERE `item_name` IN ('bonusrare20_1a*1', 'bonusrare20_1a*2', 'bonusrare20_1a*3', 'bonusrare20_1a*4', 'bonusrare20_1a*5', 'bonusrare20_1a*6', 'garden_rosearch', 'rare_colourable_beehive_bulb*2', 'rare_colourable_fan*2', 'rare_colourable_fountain*2', 'rare_colourable_scifidoor*2', 'rare_colourable_scifiport*2')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 1;

UPDATE `items_base` SET `interaction_modes_count` = 2
    WHERE `item_name` IN ('easter_c17_barngate', 'scifi_c17_airlock', 'wildwest_saloondr', 'xmas_c16_balcony')
      AND `interaction_type` = 'gate' AND `interaction_modes_count` = 1;

UPDATE `items` i JOIN `items_base` b ON b.`id` = i.`item_id`
    SET i.`extra_data` = '1'
    WHERE b.`item_name` IN ('easter_c18_recyclebin', 'xmas_c16_food2')
      AND b.`interaction_type` = 'default' AND b.`interaction_modes_count` = 3
      AND i.`extra_data` REGEXP '^[0-9]+$' AND CAST(i.`extra_data` AS UNSIGNED) >= 2;

UPDATE `items_base` SET `interaction_modes_count` = 2
    WHERE `item_name` IN ('easter_c18_recyclebin', 'xmas_c16_food2')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 3;

UPDATE `items` i JOIN `items_base` b ON b.`id` = i.`item_id`
    SET i.`extra_data` = '1'
    WHERE b.`item_name` IN ('xmas13_eggnogglass')
      AND b.`interaction_type` = 'default' AND b.`interaction_modes_count` = 4
      AND i.`extra_data` REGEXP '^[0-9]+$' AND CAST(i.`extra_data` AS UNSIGNED) >= 2;

UPDATE `items_base` SET `interaction_modes_count` = 2
    WHERE `item_name` IN ('xmas13_eggnogglass')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 4;

UPDATE `items` i JOIN `items_base` b ON b.`id` = i.`item_id`
    SET i.`extra_data` = '1'
    WHERE b.`item_name` IN ('xmas13_mincepies')
      AND b.`interaction_type` = 'default' AND b.`interaction_modes_count` = 5
      AND i.`extra_data` REGEXP '^[0-9]+$' AND CAST(i.`extra_data` AS UNSIGNED) >= 2;

UPDATE `items_base` SET `interaction_modes_count` = 2
    WHERE `item_name` IN ('xmas13_mincepies')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 5;

UPDATE `items_base` SET `interaction_modes_count` = 3
    WHERE `item_name` IN ('mall_c17_winspo')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 1;

UPDATE `items_base` SET `interaction_modes_count` = 3
    WHERE `item_name` IN ('bathroom_toilet1', 'bathroom_toilet2', 'bathroom_toilet3', 'bathroom_toilet4')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 2;

UPDATE `items_base` SET `interaction_modes_count` = 4
    WHERE `item_name` IN ('xmas11_flag')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 2;

UPDATE `items_base` SET `interaction_modes_count` = 5
    WHERE `item_name` IN ('val09_wdrobe_b', 'val09_wdrobe_g')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 2;

UPDATE `items_base` SET `interaction_modes_count` = 6
    WHERE `item_name` IN ('zengarden_c20_light')
      AND `interaction_type` = 'default' AND `interaction_modes_count` = 5;
