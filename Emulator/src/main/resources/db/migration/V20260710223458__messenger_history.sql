CREATE TABLE IF NOT EXISTS `messenger_conversations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `type` ENUM('direct','group') NOT NULL,
  `direct_key` VARCHAR(64) NULL,
  `name` VARCHAR(100) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_messenger_direct_key` (`direct_key`),
  KEY `idx_messenger_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `messenger_members` (
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `user_id` INT NOT NULL,
  `role` ENUM('member','admin') NOT NULL DEFAULT 'member',
  `joined_message_id` BIGINT UNSIGNED NULL,
  `left_message_id` BIGINT UNSIGNED NULL,
  `last_read_message_id` BIGINT UNSIGNED NULL,
  `joined_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `left_at` TIMESTAMP NULL,
  PRIMARY KEY (`conversation_id`, `user_id`),
  KEY `idx_messenger_member_user` (`user_id`, `left_at`),
  CONSTRAINT `fk_messenger_member_conversation`
    FOREIGN KEY (`conversation_id`) REFERENCES `messenger_conversations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `messenger_messages` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `sender_id` INT NOT NULL,
  `type` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `message` VARCHAR(255) NOT NULL,
  `metadata` VARCHAR(1024) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_messenger_page` (`conversation_id`, `id`),
  KEY `idx_messenger_retention` (`created_at`, `conversation_id`),
  CONSTRAINT `fk_messenger_message_conversation`
    FOREIGN KEY (`conversation_id`) REFERENCES `messenger_conversations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
