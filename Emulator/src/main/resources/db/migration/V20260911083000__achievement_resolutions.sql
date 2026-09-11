-- New Year resolutions (AIR 13 `AchievementsResolutionController`): the owner of a
-- resolution furni picks one of their own achievements and commits to reaching the
-- next level of it before the clock runs out; the furni tracks that promise.
--
-- One row per furni, because the furni is the promise: it carries whose it is, the
-- achievement, the level that has to be reached and the badge that stands for it.
--   item_id       the placed furniture in `items`
--   target_level  the level the owner has to reach, one above the level they were on
--   badge_code    `ACH_<achievement><target_level>`, what the window shows
--   ends_at       unix timestamp the promise expires at
--   completed_at  zero while the promise is open
CREATE TABLE IF NOT EXISTS `achievement_resolutions` (
    `item_id` INT NOT NULL,
    `achievement_id` INT NOT NULL,
    `badge_code` VARCHAR(64) NOT NULL DEFAULT '',
    `completed_at` INT NOT NULL DEFAULT 0,
    `ends_at` INT NOT NULL DEFAULT 0,
    `started_at` INT NOT NULL DEFAULT 0,
    `target_level` INT NOT NULL DEFAULT 0,
    `user_id` INT NOT NULL,
    PRIMARY KEY (`item_id`),
    KEY `user_achievement` (`user_id`, `achievement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
