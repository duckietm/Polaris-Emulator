-- Habbi : les îles de Lost Monkey (le singe, ses besoins, ses bananes, ses pièces, sa fusée), une par joueur, en JSON.
-- Idempotent.
CREATE TABLE IF NOT EXISTS `lostmonkey_islands` (
    `user_id` INT NOT NULL,
    `state` TEXT NOT NULL,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_lostmonkey_islands_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
