CREATE TABLE IF NOT EXISTS `snowwar_scores` (
    `user_id` INT NOT NULL,
    `week_start` DATE NOT NULL,
    `score` BIGINT NOT NULL DEFAULT 0,
    `matches` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `week_start`),
    KEY `idx_snowwar_scores_week_score` (`week_start`, `score`),
    CONSTRAINT `fk_snowwar_scores_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
