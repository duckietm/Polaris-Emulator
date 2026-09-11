-- AIR 13 "Users" packets: the session block list, the Discord Rich Presence preferences and the
-- daily respect replenishes.
--
-- Block list (official BlockedUsersManager, packets 485 / 697 / 1886 / 2649): a second list next
-- to users_ignored, keyed by user id instead of name. Blocking hides the other player's chat and
-- draws them as a blocked avatar, so it is stored per (user, target) exactly like the ignore list.
CREATE TABLE IF NOT EXISTS `users_blocked` (
    `user_id` INT NOT NULL,
    `target_id` INT NOT NULL,
    PRIMARY KEY (`user_id`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Discord preferences (official DiscordSettingsController, packets 1055 / 2774 / 1600). The
-- official value object is (version, showHabbo, shareActivity, hideInHiddenRooms, allowJoining);
-- version 0 means "never saved", which is what makes the client offer the settings popup.
CREATE TABLE IF NOT EXISTS `users_discord_settings` (
    `user_id` INT NOT NULL,
    `preference_version` INT NOT NULL DEFAULT 0,
    `show_habbo` ENUM('0','1') NOT NULL DEFAULT '1',
    `share_activity` ENUM('0','1') NOT NULL DEFAULT '1',
    `hide_in_hidden_rooms` ENUM('0','1') NOT NULL DEFAULT '1',
    `allow_joining` ENUM('0','1') NOT NULL DEFAULT '1',
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- respectReplenishesLeft of the official user object (2661): how many times the daily respects
-- can still be bought back today (SessionDataManager.replenishRespect, composer 3728).
ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `daily_respect_replenishes` INT NOT NULL DEFAULT 0;
