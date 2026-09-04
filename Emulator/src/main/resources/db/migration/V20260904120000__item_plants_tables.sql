-- Move plant lifecycle out of items.extra_data into two dedicated tables so a plant's growth
-- and death no longer depend on items_base.interaction_modes_count (the "interaction count").
-- See InteractionPlant + ItemManager.loadPlants().

-- item_plants: per furni-type config, keyed by base item name.
--   grow_counts = highest growth frame a plant can reach by watering (count_state caps here)
--   death_count = the animation frame shown when the plant dies from neglect
-- Both are frame numbers on the furni's own animation, independent of the server-side
-- interaction_modes_count. Tweak per type here without touching code.
CREATE TABLE IF NOT EXISTS `item_plants` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `item_name` VARCHAR(70) NOT NULL,
    `grow_counts` INT NOT NULL DEFAULT 1,
    `death_count` INT NOT NULL DEFAULT 2,
    PRIMARY KEY (`id`),
    UNIQUE KEY `item_name` (`item_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- item_plants_data: per placed-furni runtime state, bound 1:1 to items.id.
--   count_state     = current growth frame (0..grow_counts)
--   last_water_date = unix seconds of the last watering; also set on placement, so both the
--                     re-water cooldown and the death clock count from it
--   state           = 0 alive, 1 dead
CREATE TABLE IF NOT EXISTS `item_plants_data` (
    `item_id` INT NOT NULL,
    `count_state` INT NOT NULL DEFAULT 0,
    `last_water_date` INT NOT NULL DEFAULT 0,
    `state` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed the starter jungle flowers. Tweak grow_counts / death_count per type here. Idempotent.
INSERT INTO `item_plants` (`item_name`, `grow_counts`, `death_count`) VALUES
    ('jungle_c16_flowera1', 9, 10), ('jungle_c16_flowera2', 9, 10), ('jungle_c16_flowera3', 9, 10),
    ('jungle_c16_flowerb1', 9, 10), ('jungle_c16_flowerb2', 9, 10), ('jungle_c16_flowerb3', 9, 10),
    ('jungle_c16_flowerc1', 9, 10), ('jungle_c16_flowerc2', 9, 10), ('jungle_c16_flowerc3', 9, 10),
    ('jungle_c16_flowerd1', 9, 10), ('jungle_c16_flowerd2', 9, 10), ('jungle_c16_flowerd3', 9, 10)
ON DUPLICATE KEY UPDATE `grow_counts` = VALUES(`grow_counts`), `death_count` = VALUES(`death_count`);

-- Assign the `plants` interaction so the emulator instantiates InteractionPlant for these furni.
-- (Folded in from the removed V20260902140000; interaction_type is read at startup.) The old
-- V20260902150000 that forced interaction_modes_count is gone — the plant lifecycle now takes its
-- frame limits from item_plants above, not from the furni's interaction/state count. Idempotent.
UPDATE `items_base` SET `interaction_type` = 'plants'
WHERE `item_name` IN (
    'jungle_c16_flowera1', 'jungle_c16_flowera2', 'jungle_c16_flowera3',
    'jungle_c16_flowerb1', 'jungle_c16_flowerb2', 'jungle_c16_flowerb3',
    'jungle_c16_flowerc1', 'jungle_c16_flowerc2', 'jungle_c16_flowerc3',
    'jungle_c16_flowerd1', 'jungle_c16_flowerd2', 'jungle_c16_flowerd3'
);

-- Configurable plant timings (read by InteractionPlant via emulator_settings):
--   plant_water_secconds       = re-water cooldown in seconds (1800 = 30 min)
--   plant_water_deathtime_hour = hours of neglect before the plant dies (24)
-- Tune these per hotel. Idempotent.
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('plant_water_secconds', '1800'),
    ('plant_water_deathtime_hour', '24')
ON DUPLICATE KEY UPDATE `value` = `value`;
