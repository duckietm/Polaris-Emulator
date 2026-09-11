-- Treasure hunt (AIR 13 TreasureHuntFirstWinner / TreasureHuntFail / TreasureHuntUpdate)
-- and community goals (CommunityGoalProgress 2525 / ConcurrentUsersGoalProgress 2737).
--
-- Treasure hunt: a hunt is a set of furniture placed in rooms; clicking one of
-- those items counts as a find. The client only ever sees the hunt code, the
-- progress and the winner, so everything else lives here.
--   code                  the `huntId` string the client localizes as
--                         `treasure_hunt.<code>.name`
--   required_level        achievement score a player without Habbo Club needs
--   required_level_paying achievement score a Habbo Club member needs
--   reward_*              what the player gets when the last item is found
-- The number of steps is the number of enabled rows in `treasure_hunt_items`,
-- so a hunt can never disagree with its own item list.
CREATE TABLE IF NOT EXISTS `treasure_hunts` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `required_level` INT NOT NULL DEFAULT 0,
    `required_level_paying` INT NOT NULL DEFAULT 0,
    `reward_badge` VARCHAR(64) NOT NULL DEFAULT '',
    `reward_item_id` INT NOT NULL DEFAULT 0,
    `reward_points` INT NOT NULL DEFAULT 0,
    `reward_points_type` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One hidden item of a hunt. `item_id` is the id of the placed furniture in
-- `items`; a piece of furniture belongs to at most one hunt.
CREATE TABLE IF NOT EXISTS `treasure_hunt_items` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `hunt_id` INT NOT NULL,
    `room_id` INT NOT NULL DEFAULT 0,
    `item_id` INT NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `item_id` (`item_id`),
    KEY `hunt_id` (`hunt_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Every item a player already found, so a second click never counts twice.
CREATE TABLE IF NOT EXISTS `treasure_hunt_finds` (
    `hunt_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `item_id` INT NOT NULL,
    `found_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`hunt_id`, `user_id`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-player progress. `completed_at` orders the winners, so the first row of a
-- hunt is the one the first-winner packet announces.
CREATE TABLE IF NOT EXISTS `treasure_hunt_progress` (
    `hunt_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `steps_completed` INT NOT NULL DEFAULT 0,
    `completed` TINYINT(1) NOT NULL DEFAULT 0,
    `completed_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`hunt_id`, `user_id`),
    KEY `completed` (`hunt_id`, `completed`, `completed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Community goals (`CommunityGoalProgress`). One row per goal; the hotel view
-- asks for the goal marked `active` and gets its own progress plus the player's
-- contribution back.
--   code               the `goalCode` the client localizes
--   level_thresholds   comma-separated total scores that raise the level
--   reward_user_limits comma-separated `rewardUserLimits` of the packet: how
--                      many players each level rewards
--   vote_options       comma-separated option ids the vote packet accepts
CREATE TABLE IF NOT EXISTS `community_goals` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `active` TINYINT(1) NOT NULL DEFAULT 0,
    `total_score` INT NOT NULL DEFAULT 0,
    `level_thresholds` VARCHAR(255) NOT NULL DEFAULT '',
    `reward_user_limits` VARCHAR(255) NOT NULL DEFAULT '',
    `vote_options` VARCHAR(255) NOT NULL DEFAULT '',
    `expires_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `code` (`code`),
    KEY `active` (`active`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- What each player contributed to a goal; the rank is the position in this list.
CREATE TABLE IF NOT EXISTS `community_goal_contributions` (
    `goal_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `score` INT NOT NULL DEFAULT 0,
    `updated_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`goal_id`, `user_id`),
    KEY `score` (`goal_id`, `score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One vote per player per goal (`CommunityGoalVote` 3536).
CREATE TABLE IF NOT EXISTS `community_goal_votes` (
    `goal_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `option_id` INT NOT NULL,
    `voted_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`goal_id`, `user_id`),
    KEY `option_id` (`goal_id`, `option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Rewards already handed out for a goal code; the concurrent-users goal uses
-- the code `concurrentusers`, so the badge is only ever given once.
CREATE TABLE IF NOT EXISTS `community_goal_rewards_claimed` (
    `goal_code` VARCHAR(64) NOT NULL,
    `user_id` INT NOT NULL,
    `claimed_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`goal_code`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.treasurehunt.enabled', '1', 'Count a click on a furniture listed in treasure_hunt_items as a treasure hunt find.'),
    ('hotel.communitygoal.concurrentusers.goal', '0', 'Concurrent users goal of the hotel view (0 hides the ConcurrentUsersGoalProgress widget).'),
    ('hotel.communitygoal.concurrentusers.badge', 'ConcurrentUsersReward', 'Badge handed out by GetConcurrentUsersReward when the concurrent users goal is reached.'),
    ('hotel.selfdonation.enabled', '0', 'Enable the sandbox self donation tool (:donate). The official client only offers it on the sandbox environments; it also needs acc_debug.'),
    ('hotel.selfdonation.max.amount', '500', 'Largest amount the self donation tool accepts, like the official 1..500 range.')
ON DUPLICATE KEY UPDATE `value` = `value`;
