-- The Game Center's games played through the emulator's HTTP API (UNO, Werewolf, Wobble Squabble, BattleBall,
-- Fast Food in HTML5, the game hall and its games, Lost Monkey). The games run in memory; these tables keep each
-- player's results, the games played each day (free, then bought), the weekly leaderboards, the packs of games to
-- buy, the rewards and Lost Monkey's islands. Idempotent.
--
-- Settings (emulator_settings): <game>.enabled, <game>.games.max / tables.max, uno.turn.seconds, uno.players.max,
-- hall.turn.seconds, hall.quiz.language (en, fr, de, es, it, nl, ar). Free games a day, the club's extra games, the
-- packs and the rewards of each game are set in housekeeping (Games tab, permission acc_hk_games).
-- Texts (emulator_texts, English when unset): games.bots.<game> (robots' names separated by ;),
-- gamecenter.reward.game, gamecenter.reward.week, gamecenter.reward.gift, gamecenter.currency.<credits|duckets|
-- diamonds|points>.

-- The Flash Fast Food cannot be played any more: the HTML5 one replaces it in the list.
UPDATE `emulator_settings` SET `value` = 'false' WHERE `key` = 'gamecenter.fastfood.enabled';

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
  ('gamecenter.games.free.daily.hc', '20', 'SnowStorm: free games a day for a club member (never fewer than gamecenter.games.free.daily; -1: unlimited).'),
  ('uno.enabled', 'true', 'UNO in the Game Center.'),
  ('uno.turn.seconds', '25', 'UNO: seconds to play a turn (then the player draws and passes; three times in a row, they leave the table).'),
  ('uno.tables.max', '20', 'UNO: tables open at the same time.'),
  ('uno.players.max', '6', 'UNO: players at most at a table (2 to 6).'),
  ('werewolf.enabled', 'true', 'Werewolf in the Game Center.'),
  ('werewolf.games.max', '10', 'Werewolf: villages open at the same time.'),
  ('wobble.enabled', 'true', 'Wobble Squabble in the Game Center.'),
  ('wobble.games.max', '10', 'Wobble Squabble: duels open at the same time.'),
  ('battleball.enabled', 'true', 'BattleBall in the Game Center.'),
  ('battleball.games.max', '10', 'BattleBall: games open at the same time.'),
  ('fastfood.enabled', 'true', 'Fast Food (HTML5) in the Game Center.'),
  ('fastfood.games.max', '10', 'Fast Food: games open at the same time.'),
  ('hall.enabled', 'true', 'The game hall (tic-tac-toe, four in a row, battleships, chess, poker) and its games in the Game Center.'),
  ('hall.tables.max', '40', 'Game hall: tables open at the same time.'),
  ('hall.turn.seconds', '60', 'Game hall: time to play a move (then a move is played for the player).'),
  ('hall.quiz.language', 'en', 'Language of the quiz questions (en, fr, de, es, it, nl, ar).');

CREATE TABLE IF NOT EXISTS `uno_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `werewolf_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `wobble_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  `rounds_won` int(11) NOT NULL DEFAULT 0,
  `rounds_lost` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `battleball_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  `points` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `fastfood_stats` (
  `user_id` int(11) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  `stars` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `hall_stats` (
  `user_id` int(11) NOT NULL,
  `game` varchar(20) NOT NULL,
  `games` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `game`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `game_plays` (
    `user_id` INT NOT NULL,
    `game` VARCHAR(24) NOT NULL,
    `free_day` DATE NULL DEFAULT NULL,
    `free_used` INT NOT NULL DEFAULT 0,
    `extra` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `game`),
    CONSTRAINT `fk_game_plays_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `lostmonkey_islands` (
    `user_id` INT NOT NULL,
    `state` TEXT NOT NULL,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_lostmonkey_islands_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `gamecenter_scores` (
    `user_id` INT NOT NULL,
    `game` VARCHAR(24) NOT NULL,
    `week_start` DATE NOT NULL,
    `score` INT NOT NULL DEFAULT 0,
    `matches` INT NOT NULL DEFAULT 0,
    `wins` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `game`, `week_start`),
    KEY `idx_gamecenter_scores_week` (`game`, `week_start`, `score`),
    CONSTRAINT `fk_gamecenter_scores_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `gamecenter_settings` (
    `game` VARCHAR(24) NOT NULL,
    `free_daily` INT NOT NULL DEFAULT 10,
    `hc_extra` INT NOT NULL DEFAULT 10,
    PRIMARY KEY (`game`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `gamecenter_settings` (`game`, `free_daily`, `hc_extra`) VALUES
    ('uno', 10, 10),
    ('werewolf', 10, 10),
    ('wobble', 10, 10),
    ('wobblenext', 10, 10),
    ('battleball', 10, 10),
    ('fastfood', 10, 10),
    ('hall', 10, 10),
    ('snails', 10, 10),
    ('quiz', 10, 10),
    ('tetris', 10, 10),
    ('diving', 10, 10),
    ('kart', 10, 10),
    ('copywriter', 10, 10);

CREATE TABLE IF NOT EXISTS `gamecenter_offers` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `game` VARCHAR(24) NOT NULL,
    `games` INT NOT NULL,
    `price` INT NOT NULL,
    `currency` INT NOT NULL DEFAULT -1,
    `order_num` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_gamecenter_offers_game` (`game`, `order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `gamecenter_offers` (`game`, `games`, `price`, `currency`, `order_num`)
SELECT seed.* FROM (
    SELECT 'uno' AS game, 10 AS games, 10 AS price, -1 AS currency, 1 AS order_num UNION ALL
    SELECT 'uno', 100, 80, -1, 2 UNION ALL
    SELECT 'uno', 300, 200, -1, 3 UNION ALL
    SELECT 'werewolf', 10, 10, -1, 1 UNION ALL
    SELECT 'werewolf', 100, 80, -1, 2 UNION ALL
    SELECT 'werewolf', 300, 200, -1, 3 UNION ALL
    SELECT 'wobble', 10, 10, -1, 1 UNION ALL
    SELECT 'wobble', 100, 80, -1, 2 UNION ALL
    SELECT 'wobble', 300, 200, -1, 3 UNION ALL
    SELECT 'wobblenext', 10, 10, -1, 1 UNION ALL
    SELECT 'wobblenext', 100, 80, -1, 2 UNION ALL
    SELECT 'wobblenext', 300, 200, -1, 3 UNION ALL
    SELECT 'battleball', 10, 10, -1, 1 UNION ALL
    SELECT 'battleball', 100, 80, -1, 2 UNION ALL
    SELECT 'battleball', 300, 200, -1, 3 UNION ALL
    SELECT 'fastfood', 10, 10, -1, 1 UNION ALL
    SELECT 'fastfood', 100, 80, -1, 2 UNION ALL
    SELECT 'fastfood', 300, 200, -1, 3 UNION ALL
    SELECT 'hall', 10, 10, -1, 1 UNION ALL
    SELECT 'hall', 100, 80, -1, 2 UNION ALL
    SELECT 'hall', 300, 200, -1, 3 UNION ALL
    SELECT 'snails', 10, 10, -1, 1 UNION ALL
    SELECT 'snails', 100, 80, -1, 2 UNION ALL
    SELECT 'snails', 300, 200, -1, 3 UNION ALL
    SELECT 'quiz', 10, 10, -1, 1 UNION ALL
    SELECT 'quiz', 100, 80, -1, 2 UNION ALL
    SELECT 'quiz', 300, 200, -1, 3 UNION ALL
    SELECT 'tetris', 10, 10, -1, 1 UNION ALL
    SELECT 'tetris', 100, 80, -1, 2 UNION ALL
    SELECT 'tetris', 300, 200, -1, 3 UNION ALL
    SELECT 'diving', 10, 10, -1, 1 UNION ALL
    SELECT 'diving', 100, 80, -1, 2 UNION ALL
    SELECT 'diving', 300, 200, -1, 3 UNION ALL
    SELECT 'kart', 10, 10, -1, 1 UNION ALL
    SELECT 'kart', 100, 80, -1, 2 UNION ALL
    SELECT 'kart', 300, 200, -1, 3 UNION ALL
    SELECT 'copywriter', 10, 10, -1, 1 UNION ALL
    SELECT 'copywriter', 100, 80, -1, 2 UNION ALL
    SELECT 'copywriter', 300, 200, -1, 3
) seed
WHERE NOT EXISTS (SELECT 1 FROM `gamecenter_offers` existing WHERE existing.`game` = seed.game);

CREATE TABLE IF NOT EXISTS `gamecenter_rewards` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `game` VARCHAR(24) NOT NULL,
    `kind` ENUM('play', 'win', 'week') NOT NULL,
    `rank_from` INT NOT NULL DEFAULT 1,
    `rank_to` INT NOT NULL DEFAULT 1,
    `reward_type` ENUM('furni', 'clothing', 'badge', 'currency') NOT NULL,
    `reward_value` VARCHAR(64) NOT NULL DEFAULT '',
    `amount` INT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `idx_gamecenter_rewards_game` (`game`, `kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `gamecenter_reward_weeks` (
    `game` VARCHAR(24) NOT NULL,
    `week_start` DATE NOT NULL,
    `paid_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`game`, `week_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `gamecenter_reward_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `game` VARCHAR(24) NOT NULL,
    `kind` VARCHAR(8) NOT NULL,
    `reward_type` VARCHAR(12) NOT NULL,
    `reward_value` VARCHAR(64) NOT NULL,
    `amount` INT NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_gamecenter_reward_log_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- acc_hk_games: the Games tab of housekeeping. It starts with the values of acc_hk_hotel (who already sets the hotel's
-- tools), only when the key is created, never over a hotel's own choice on a rerun.
SET @games_existed = (SELECT COUNT(*) FROM `permission_definitions` WHERE `permission_key` = 'acc_hk_games');

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_hk_games', 1, 'Housekeeping: set the games (free games a day, packs of games and their prices, club bonus, rewards).')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @copy_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('target.`', c.`column_name`, '` = source.`', c.`column_name`, '`') ORDER BY r.`id` SEPARATOR ', ')
  INTO @copy_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @copy_sql = IF(@copy_sql IS NULL OR @games_existed > 0,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` target',
           ' JOIN `permission_definitions` source ON source.`permission_key` = ''acc_hk_hotel''',
           ' SET ', @copy_sql,
           ' WHERE target.`permission_key` = ''acc_hk_games'''));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;
