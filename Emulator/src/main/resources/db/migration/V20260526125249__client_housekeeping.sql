INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES ('acc_housekeeping', '1', 'Allow housekeeping in the client', '0', '0', '0', '0', '0', '0', '1')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);


CREATE TABLE IF NOT EXISTS `housekeeping_log` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `timestamp` INT NOT NULL,
  `actor_id` INT NOT NULL,
  `actor_name` VARCHAR(64) NOT NULL DEFAULT '',
  `target_type` VARCHAR(16) NOT NULL DEFAULT 'user',
  `target_id` INT NOT NULL DEFAULT 0,
  `target_label` VARCHAR(128) NOT NULL DEFAULT '',
  `action` VARCHAR(64) NOT NULL DEFAULT '',
  `detail` VARCHAR(500) NOT NULL DEFAULT '',
  `success` TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
