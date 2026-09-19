-- Reward track sample: staggered rows and furni prizes.
--
-- The premium row of the sample track no longer sits on the same milestones
-- as the free row: its prizes move to 40 / 90 / 160 / 240 points, so the two
-- rows alternate along the bar like the official track. Only rows still at
-- their seeded values move, an edited hotel keeps its numbers. Two furni
-- prizes close the track: reward_type 'furni' hands out reward_amount copies
-- of the items_base item named in extra_params. Every statement is idempotent.

UPDATE `reward_track_prizes` SET `required_points` = 40  WHERE `track_id` = 'season_1' AND `id` = 'p1_premium' AND `required_points` = 20;
UPDATE `reward_track_prizes` SET `required_points` = 90  WHERE `track_id` = 'season_1' AND `id` = 'p2_premium' AND `required_points` = 60;
UPDATE `reward_track_prizes` SET `required_points` = 160 WHERE `track_id` = 'season_1' AND `id` = 'p3_premium' AND `required_points` = 120;
UPDATE `reward_track_prizes` SET `required_points` = 240 WHERE `track_id` = 'season_1' AND `id` = 'p4_premium' AND `required_points` = 200;

INSERT IGNORE INTO `reward_track_prizes`
    (`track_id`, `id`, `required_points`, `product_item_type_id`, `reward_type`, `extra_params`, `reward_amount`, `premium`, `sort_order`)
VALUES
    ('season_1', 'p5', 300, 0, 'furni', 'club_sofa', 1, 0, 9),
    ('season_1', 'p5_premium', 320, 0, 'furni', 'throne', 1, 1, 10);
