-- AIR game-token offers (GetSnowWarGameTokensOffer 980 / SnowWarGameTokens
-- 3419 / PurchaseSnowWarGameTokensOffer 391): the three "get more games"
-- buttons of the game hub and the extra games they credit.
CREATE TABLE IF NOT EXISTS `snowwar_token_offers` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `localization_id` VARCHAR(64) NOT NULL,
    `price_credits` INT NOT NULL DEFAULT 0,
    `price_points` INT NOT NULL DEFAULT 0,
    `points_type` INT NOT NULL DEFAULT 0,
    `games` INT NOT NULL DEFAULT 0,
    `enabled` ENUM('0','1') NOT NULL DEFAULT '1',
    `order_num` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_snowwar_token_offers_localization` (`localization_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `snowwar_token_offers`
    (`localization_id`, `price_credits`, `price_points`, `points_type`, `games`, `enabled`, `order_num`)
VALUES
    ('GET_SNOWWAR_TOKENS', 10, 0, 0, 10, '1', 1),
    ('GET_SNOWWAR_TOKENS2', 80, 0, 0, 100, '1', 2),
    ('GET_SNOWWAR_TOKENS3', 200, 0, 0, 300, '1', 3)
ON DUPLICATE KEY UPDATE `localization_id` = VALUES(`localization_id`);

CREATE TABLE IF NOT EXISTS `snowwar_game_tokens` (
    `user_id` INT NOT NULL,
    `games` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_snowwar_game_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
