CREATE TABLE IF NOT EXISTS `logs_economy` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `operation_id` VARCHAR(96) NOT NULL,
    `user_id` INT UNSIGNED NOT NULL,
    `operation` VARCHAR(64) NOT NULL,
    `currency_type` INT NOT NULL,
    `amount` INT NOT NULL,
    `balance_before` INT NOT NULL,
    `balance_after` INT NOT NULL,
    `item_id` INT UNSIGNED NULL,
    `context` VARCHAR(255) NOT NULL DEFAULT '',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_logs_economy_operation_id` (`operation_id`),
    KEY `idx_logs_economy_user_created` (`user_id`, `created_at`),
    KEY `idx_logs_economy_operation_created` (`operation`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
