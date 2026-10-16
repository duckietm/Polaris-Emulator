-- SnowWar games left (AIR games_main counter): the free daily games a user
-- has used, next to the bought games. free_used counts for free_day only; a
-- new day starts again at 0. A match charges one game when it starts.
ALTER TABLE `snowwar_game_tokens`
    ADD COLUMN IF NOT EXISTS `free_day` DATE NULL DEFAULT NULL AFTER `games`,
    ADD COLUMN IF NOT EXISTS `free_used` INT NOT NULL DEFAULT 0 AFTER `free_day`;

-- Free games per day (-1 = unlimited, no counter). Bought games come on top.
INSERT INTO emulator_settings (`key`, `value`) VALUES
	('gamecenter.games.free.daily', '10')
ON DUPLICATE KEY UPDATE `value` = `value`;
