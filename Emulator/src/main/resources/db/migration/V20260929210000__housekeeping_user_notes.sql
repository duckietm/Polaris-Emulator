-- Internal staff notes on a user, shown on the user's housekeeping page.
CREATE TABLE IF NOT EXISTS `housekeeping_user_notes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `author_id` INT NOT NULL,
  `author_name` VARCHAR(64) NOT NULL DEFAULT '',
  `note` VARCHAR(500) NOT NULL DEFAULT '',
  `created_at` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
