-- Polaris-owned pet breeding reference data. This is a small data-only update:
-- no hotel pets, rooms, users, or operator content are deleted.

CREATE TEMPORARY TABLE `polaris_expected_pet_breeds` (
  `pet_type` int(11) NOT NULL,
  `rarity_level` int(11) NOT NULL,
  `breed` int(11) NOT NULL,
  PRIMARY KEY (`pet_type`, `rarity_level`, `breed`)
) ENGINE=InnoDB;

INSERT INTO `polaris_expected_pet_breeds` (`pet_type`, `rarity_level`, `breed`)
SELECT pets.pet_type,
       CASE
         WHEN breeds.breed <= 7 THEN 1
         WHEN breeds.breed <= 12 THEN 2
         WHEN breeds.breed <= 16 THEN 3
         ELSE 4
       END,
       breeds.breed
FROM (
  SELECT 24 AS pet_type UNION ALL
  SELECT 25 UNION ALL
  SELECT 28 UNION ALL
  SELECT 29 UNION ALL
  SELECT 30
) pets
CROSS JOIN (
  SELECT 0 AS breed UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
  SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
  SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL
  SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL
  SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
) breeds;

-- Arc MS 3.5.5 ships one known off-by-one baby-cat set (breeds 1..20).
-- Replace only that exact legacy shape; custom datasets are otherwise retained.
SET @legacy_baby_cat_rows := (
  SELECT COUNT(*) = 20
     AND MIN(`breed`) = 1
     AND MAX(`breed`) = 20
     AND SUM(`breed` = 0) = 0
  FROM `pet_breeding_races`
  WHERE `pet_type` = 28
);
DELETE FROM `pet_breeding_races`
WHERE `pet_type` = 28 AND @legacy_baby_cat_rows = 1;

INSERT INTO `pet_breeding_races` (`pet_type`, `rarity_level`, `breed`)
SELECT `pet_type`, `rarity_level`, `breed`
FROM `polaris_expected_pet_breeds`
WHERE NOT EXISTS (
  SELECT 1
  FROM `pet_breeding_races` existing
  WHERE existing.`pet_type` = `polaris_expected_pet_breeds`.`pet_type`
    AND existing.`rarity_level` = `polaris_expected_pet_breeds`.`rarity_level`
    AND existing.`breed` = `polaris_expected_pet_breeds`.`breed`
);

DROP TEMPORARY TABLE `polaris_expected_pet_breeds`;

INSERT INTO `pet_breeding` (`pet_id`, `offspring_id`)
SELECT expected.`pet_id`, expected.`offspring_id`
FROM (
  SELECT 0 AS `pet_id`, 29 AS `offspring_id` UNION ALL
  SELECT 1, 28 UNION ALL
  SELECT 3, 25 UNION ALL
  SELECT 4, 24 UNION ALL
  SELECT 5, 30
) expected
WHERE NOT EXISTS (
  SELECT 1
  FROM `pet_breeding` existing
  WHERE existing.`pet_id` = expected.`pet_id`
    AND existing.`offspring_id` = expected.`offspring_id`
);

UPDATE `pet_actions` SET `offspring_type` = 29 WHERE `pet_type` = 0;
UPDATE `pet_actions` SET `offspring_type` = 28 WHERE `pet_type` = 1;
UPDATE `pet_actions` SET `offspring_type` = 25 WHERE `pet_type` = 3;
UPDATE `pet_actions` SET `offspring_type` = 24 WHERE `pet_type` = 4;
UPDATE `pet_actions` SET `offspring_type` = 30 WHERE `pet_type` = 5;

-- Correct only the five known Arc breeding boxes. A plugin may add other
-- pet_breeding_* items with its own interaction contract.
UPDATE `items_base`
SET `interaction_type` = 'breeding_nest'
WHERE `id` IN (4796, 4825, 6346, 6347, 6348)
  AND `item_name` LIKE 'pet_breeding_%'
  AND `interaction_type` <> 'breeding_nest';
