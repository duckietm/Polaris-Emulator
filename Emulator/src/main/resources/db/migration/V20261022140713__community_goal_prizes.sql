-- Prizes of the community goals (AIR GetCommunityGoalEarnedPrizes 2688 ->
-- CommunityGoalEarnedPrizes 3319, RedeemCommunityGoalPrize 90).
--
-- `prize_level` is the rank bucket of `community_goals.reward_user_limits`:
-- level 1 rewards ranks 1..limits[0], level 2 the next limits[1] ranks, and so
-- on, exactly what the hotel view prizes widget prints. A player earns the prize
-- of their bucket once the goal expired (`expires_at`); redeeming books a row in
-- `community_goal_rewards_claimed` under the goal code.
--
-- `reward_code` is a badge code when `is_badge` = 1, otherwise the
-- `items_base.item_name` of the furni handed out. A trophy gets the player's
-- name, the date and `trophy_inscription` (`%rank%` / `{0}` = the final rank;
-- empty = "<goal code> #<rank>").
CREATE TABLE IF NOT EXISTS `community_goal_prizes` (
    `goal_id` INT NOT NULL,
    `prize_level` INT NOT NULL,
    `reward_code` VARCHAR(100) NOT NULL DEFAULT '',
    `is_badge` TINYINT(1) NOT NULL DEFAULT 0,
    `localized_name` VARCHAR(100) NOT NULL DEFAULT '',
    `trophy_inscription` VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (`goal_id`, `prize_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.communitygoal.halloffame.size', '10', 'Players the community goal hall of fame of the hotel view lists (GetCommunityGoalHallOfFame 2167), 1..10 like the official widget.')
ON DUPLICATE KEY UPDATE `value` = `value`;
