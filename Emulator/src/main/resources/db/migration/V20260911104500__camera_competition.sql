-- Photo competition (AIR 13 camera checkout: "Submit to the competition").
--
-- One row per submission. The official texts say a player may submit a few
-- times a day and that only their last submission counts, so nothing is
-- replaced here: the rows are the history and the newest row of a player is
-- the entry that stands.
--   url           the published photo, the same value `camera_web` stores
--   submitted_at  unix timestamp, which is also what the daily count uses
CREATE TABLE IF NOT EXISTS `camera_competition_entries` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `room_id` INT NOT NULL DEFAULT 0,
    `submitted_at` INT NOT NULL DEFAULT 0,
    `url` VARCHAR(255) NOT NULL DEFAULT '',
    `user_id` INT NOT NULL,
    PRIMARY KEY (`id`),
    KEY `user_submitted` (`user_id`, `submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
