-- The official Furni-Matic achievements, which the emulator now progresses:
--   RecycledItems       one per furni put into an exchange
--   RecycleBoxOpener    one per recycler box opened
--   RecycleEcoprizes    one per prize that came out of a box
--   RecyclerRarePrize   a prize of level 3 or higher
--   RecyclerGrandPrize  a prize of level 5 or higher
-- Their badges are ACH_<name><level>, as the client's texts name them (badge_name_ACH_RecycledItems, ...).
--
-- Each is added only where the hotel does not define an achievement of that name at all, so a hotel's own levels
-- and rewards are never touched. The levels follow the pattern of the existing FurnimaticQuest: Duckets (reward
-- type 0) of 5 x level and the same points; change them freely. Idempotent.

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT seed.`name`, 'explore', seed.`level`, seed.`reward_amount`, 0, seed.`points`, seed.`progress_needed`
FROM (
    SELECT 'RecycledItems' AS `name`, 1 AS `level`, 5 AS `reward_amount`, 5 AS `points`, 8 AS `progress_needed`
    UNION ALL SELECT 'RecycledItems', 2, 10, 5, 24
    UNION ALL SELECT 'RecycledItems', 3, 15, 10, 56
    UNION ALL SELECT 'RecycledItems', 4, 20, 15, 120
    UNION ALL SELECT 'RecycledItems', 5, 25, 20, 240
    UNION ALL SELECT 'RecycledItems', 6, 30, 25, 480
    UNION ALL SELECT 'RecycledItems', 7, 35, 30, 960
    UNION ALL SELECT 'RecycledItems', 8, 40, 35, 1600
    UNION ALL SELECT 'RecycledItems', 9, 45, 40, 3200
    UNION ALL SELECT 'RecycledItems', 10, 50, 45, 6400
) seed
WHERE NOT EXISTS (SELECT 1 FROM (SELECT `name` FROM `achievements`) present WHERE present.`name` = 'RecycledItems');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT seed.`name`, 'explore', seed.`level`, seed.`reward_amount`, 0, seed.`points`, seed.`progress_needed`
FROM (
    SELECT 'RecycleBoxOpener' AS `name`, 1 AS `level`, 5 AS `reward_amount`, 5 AS `points`, 1 AS `progress_needed`
    UNION ALL SELECT 'RecycleBoxOpener', 2, 10, 5, 3
    UNION ALL SELECT 'RecycleBoxOpener', 3, 15, 10, 7
    UNION ALL SELECT 'RecycleBoxOpener', 4, 20, 15, 15
    UNION ALL SELECT 'RecycleBoxOpener', 5, 25, 20, 30
    UNION ALL SELECT 'RecycleBoxOpener', 6, 30, 25, 60
    UNION ALL SELECT 'RecycleBoxOpener', 7, 35, 30, 100
    UNION ALL SELECT 'RecycleBoxOpener', 8, 40, 35, 200
    UNION ALL SELECT 'RecycleBoxOpener', 9, 45, 40, 350
    UNION ALL SELECT 'RecycleBoxOpener', 10, 50, 45, 500
) seed
WHERE NOT EXISTS (SELECT 1 FROM (SELECT `name` FROM `achievements`) present WHERE present.`name` = 'RecycleBoxOpener');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT seed.`name`, 'explore', seed.`level`, seed.`reward_amount`, 0, seed.`points`, seed.`progress_needed`
FROM (
    SELECT 'RecycleEcoprizes' AS `name`, 1 AS `level`, 5 AS `reward_amount`, 5 AS `points`, 1 AS `progress_needed`
    UNION ALL SELECT 'RecycleEcoprizes', 2, 10, 5, 3
    UNION ALL SELECT 'RecycleEcoprizes', 3, 15, 10, 7
    UNION ALL SELECT 'RecycleEcoprizes', 4, 20, 15, 15
    UNION ALL SELECT 'RecycleEcoprizes', 5, 25, 20, 30
    UNION ALL SELECT 'RecycleEcoprizes', 6, 30, 25, 60
    UNION ALL SELECT 'RecycleEcoprizes', 7, 35, 30, 100
    UNION ALL SELECT 'RecycleEcoprizes', 8, 40, 35, 200
    UNION ALL SELECT 'RecycleEcoprizes', 9, 45, 40, 350
    UNION ALL SELECT 'RecycleEcoprizes', 10, 50, 45, 500
) seed
WHERE NOT EXISTS (SELECT 1 FROM (SELECT `name` FROM `achievements`) present WHERE present.`name` = 'RecycleEcoprizes');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT seed.`name`, 'explore', seed.`level`, seed.`reward_amount`, 0, seed.`points`, seed.`progress_needed`
FROM (
    SELECT 'RecyclerRarePrize' AS `name`, 1 AS `level`, 5 AS `reward_amount`, 5 AS `points`, 1 AS `progress_needed`
    UNION ALL SELECT 'RecyclerRarePrize', 2, 10, 5, 2
    UNION ALL SELECT 'RecyclerRarePrize', 3, 15, 10, 5
    UNION ALL SELECT 'RecyclerRarePrize', 4, 20, 15, 10
    UNION ALL SELECT 'RecyclerRarePrize', 5, 25, 20, 20
    UNION ALL SELECT 'RecyclerRarePrize', 6, 30, 25, 35
    UNION ALL SELECT 'RecyclerRarePrize', 7, 35, 30, 50
    UNION ALL SELECT 'RecyclerRarePrize', 8, 40, 35, 75
    UNION ALL SELECT 'RecyclerRarePrize', 9, 45, 40, 100
    UNION ALL SELECT 'RecyclerRarePrize', 10, 50, 45, 150
) seed
WHERE NOT EXISTS (SELECT 1 FROM (SELECT `name` FROM `achievements`) present WHERE present.`name` = 'RecyclerRarePrize');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT seed.`name`, 'explore', seed.`level`, seed.`reward_amount`, 0, seed.`points`, seed.`progress_needed`
FROM (
    SELECT 'RecyclerGrandPrize' AS `name`, 1 AS `level`, 5 AS `reward_amount`, 5 AS `points`, 1 AS `progress_needed`
    UNION ALL SELECT 'RecyclerGrandPrize', 2, 10, 5, 2
    UNION ALL SELECT 'RecyclerGrandPrize', 3, 15, 10, 3
    UNION ALL SELECT 'RecyclerGrandPrize', 4, 20, 15, 5
    UNION ALL SELECT 'RecyclerGrandPrize', 5, 25, 20, 8
    UNION ALL SELECT 'RecyclerGrandPrize', 6, 30, 25, 12
    UNION ALL SELECT 'RecyclerGrandPrize', 7, 35, 30, 16
    UNION ALL SELECT 'RecyclerGrandPrize', 8, 40, 35, 20
    UNION ALL SELECT 'RecyclerGrandPrize', 9, 45, 40, 25
    UNION ALL SELECT 'RecyclerGrandPrize', 10, 50, 45, 30
) seed
WHERE NOT EXISTS (SELECT 1 FROM (SELECT `name` FROM `achievements`) present WHERE present.`name` = 'RecyclerGrandPrize');
