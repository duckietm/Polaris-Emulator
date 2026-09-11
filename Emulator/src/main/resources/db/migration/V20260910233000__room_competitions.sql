-- Room competitions (AIR 13 `RoomCompetitionController`): a hotel-wide contest
-- where owners submit a room during the submission window and everybody else
-- votes for the rooms they visit during the voting window.
--
-- The client only ever knows the competition's `code` (it localizes every text
-- as `roomcompetition.<code>.*`), so the schedule, the entry rules and the
-- tally live here.
--   code             the goal code the client sends back on every packet
--   required_furni   comma separated `items_base.item_name` values a room must
--                    contain to be submittable; empty means no requirement
--   votes_per_user   how many rooms one account may vote for, over the whole
--                    competition
--   submit_/vote_    unix timestamps; a window is open when now is between its
--                    two bounds, and the two windows may not overlap
CREATE TABLE IF NOT EXISTS `room_competitions` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `name` VARCHAR(128) NOT NULL DEFAULT '',
    `required_furni` VARCHAR(512) NOT NULL DEFAULT '',
    `submit_ends` INT NOT NULL DEFAULT 0,
    `submit_starts` INT NOT NULL DEFAULT 0,
    `vote_ends` INT NOT NULL DEFAULT 0,
    `vote_starts` INT NOT NULL DEFAULT 0,
    `votes_per_user` INT NOT NULL DEFAULT 3,
    PRIMARY KEY (`id`),
    UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One room somebody submitted. A room enters a competition once, and an owner
-- enters each competition with a single room: both are enforced by the keys, so
-- a crafted packet cannot stuff the contest.
CREATE TABLE IF NOT EXISTS `room_competition_entries` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `competition_id` INT NOT NULL,
    `room_id` INT NOT NULL,
    `submitted_at` INT NOT NULL DEFAULT 0,
    `user_id` INT NOT NULL,
    `votes` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `competition_room` (`competition_id`, `room_id`),
    UNIQUE KEY `competition_user` (`competition_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One vote. The primary key is what stops a second vote for the same room, and
-- the `competition_user` key is what counts the votes somebody has left.
CREATE TABLE IF NOT EXISTS `room_competition_votes` (
    `competition_id` INT NOT NULL,
    `entry_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `voted_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`competition_id`, `entry_id`, `user_id`),
    KEY `competition_user` (`competition_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
