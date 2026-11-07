-- Habbi : le Game Center pour tous les jeux (SnowStorm garde les siens). Les parties du jour, gratuites puis achetées,
-- jeu par jeu, et les points de chaque joueur par semaine (classements de la semaine et de toujours). Idempotent.
CREATE TABLE IF NOT EXISTS `game_plays` (
    `user_id` INT NOT NULL,
    `game` VARCHAR(24) NOT NULL,
    `free_day` DATE NULL DEFAULT NULL,
    `free_used` INT NOT NULL DEFAULT 0,
    `extra` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `game`),
    CONSTRAINT `fk_game_plays_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `game_scores` (
    `user_id` INT NOT NULL,
    `game` VARCHAR(24) NOT NULL,
    `week_start` DATE NOT NULL,
    `score` INT NOT NULL DEFAULT 0,
    `matches` INT NOT NULL DEFAULT 0,
    `wins` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `game`, `week_start`),
    KEY `idx_game_scores_week` (`game`, `week_start`, `score`),
    CONSTRAINT `fk_game_scores_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
