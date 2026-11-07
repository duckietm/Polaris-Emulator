-- Habbi : la configuration du Game Center depuis la Modération (onglet Jeux). Pour chaque jeu : les parties gratuites
-- par jour, les parties gratuites en plus avec le Habbo Club, les packs de parties à acheter (prix et monnaie : -1 = crédits,
-- sinon le type de points), et les gains (mobi, tenue, badge, monnaie) à la fin d'une partie (tous les joueurs, ou le
-- gagnant) et selon le classement de la semaine. Les points des classements passent dans `gamecenter_scores` (le nom
-- `game_scores` était déjà pris par une autre table). SnowStorm garde ses tables et ses réglages. Idempotent.
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

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_hk_games', 1, 'Configurer les jeux dans la Modération : parties gratuites, prix des parties, bonus du Habbo Club, gains.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = IF(', r.`level`, ' >= 7, 1, `', c.`column_name`, '`)') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE() AND c.`table_name` = 'permission_definitions';
SET @grant_sql = IF(@grant_sql IS NULL, 'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''acc_hk_games'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
