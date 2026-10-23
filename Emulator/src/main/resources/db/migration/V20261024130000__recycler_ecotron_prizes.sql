-- The official Furnimatic (Ecotron) prize list per rarity level, looked up by item_name so the ids of each hotel's
-- items_base do not matter. Only adds what is missing: prizes a hotel added itself are kept.
--   1 Common:       Fruit Bowl 1, Fruit Bowl 2, Potted Cactus 3, Eco Stool 1, Eco Stool 2
--   2 Uncommon:     Eco Armchair 1-3, Eco Coffee Table 2, Eco Coffee Table 3
--   3 Arcane:       Eco Curtain 1, Eco Curtain 2, Eco Lamp 1, Eco Lamp 3, Eco Light 1
--   4 Phenomenal:   Eco Mushroom 1, Eco Mushroom 2, Palm Chair
--   5 Urban Legend: Polar Sofa

INSERT INTO `recycler_prizes` (`rarity`, `item_id`)
SELECT prize.rarity, base.id
FROM (
    SELECT 1 AS rarity, 'eco_fruits1' AS item_name UNION ALL
    SELECT 1, 'eco_fruits2' UNION ALL
    SELECT 1, 'eco_cactus3' UNION ALL
    SELECT 1, 'eco_chair1' UNION ALL
    SELECT 1, 'eco_chair2' UNION ALL
    SELECT 2, 'eco_sofa1' UNION ALL
    SELECT 2, 'eco_sofa2' UNION ALL
    SELECT 2, 'eco_sofa3' UNION ALL
    SELECT 2, 'eco_table2' UNION ALL
    SELECT 2, 'eco_table3' UNION ALL
    SELECT 3, 'eco_curtains1' UNION ALL
    SELECT 3, 'eco_curtains2' UNION ALL
    SELECT 3, 'eco_lamp1' UNION ALL
    SELECT 3, 'eco_lamp3' UNION ALL
    SELECT 3, 'eco_light1' UNION ALL
    SELECT 4, 'eco_mush1' UNION ALL
    SELECT 4, 'eco_mush2' UNION ALL
    SELECT 4, 'rclr_chair' UNION ALL
    SELECT 5, 'rclr_sofa'
) AS prize
JOIN (
    SELECT item_name, MIN(id) AS id FROM `items_base` GROUP BY item_name
) AS base ON base.item_name = prize.item_name
WHERE NOT EXISTS (
    SELECT 1 FROM `recycler_prizes` AS existing WHERE existing.rarity = prize.rarity AND existing.item_id = base.id
);

-- The official list has no mythic (level 6) prize; a level without prizes makes a recycle fail when it is drawn, so
-- level 6 stays off until the hotel gives it prizes (then set hotel.ecotron.rarity.chance.6, e.g. 5000).
UPDATE `emulator_settings`
SET `value` = '0'
WHERE `key` = 'hotel.ecotron.rarity.chance.6'
  AND NOT EXISTS (SELECT 1 FROM `recycler_prizes` WHERE `rarity` = 6);
