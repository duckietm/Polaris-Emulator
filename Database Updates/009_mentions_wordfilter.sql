CREATE TABLE IF NOT EXISTS `habbo_mentions` (
  `id`              INT NOT NULL AUTO_INCREMENT,
  `target_user_id`  INT NOT NULL,
  `sender_user_id`  INT NOT NULL,
  `sender_username` VARCHAR(64) NOT NULL DEFAULT '',
  `room_id`         INT NOT NULL,
  `room_name`       VARCHAR(255) NOT NULL DEFAULT '',
  `message`         VARCHAR(255) NOT NULL DEFAULT '',
  `mention_type`    TINYINT NOT NULL DEFAULT 0,
  `timestamp`       INT NOT NULL DEFAULT 0,
  `read`            TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_target_read` (`target_user_id`, `read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
  ('mentions.enabled', '1'),
  ('mentions.room.aliases', 'amici,friends,all,everyone,tutti,room,stanza'),
  ('mentions.max.targets', '50'),
  ('mentions.cooldown.ms', '3000'),
  ('mentions.store.limit', '50');


ALTER TABLE `wordfilter`
ADD COLUMN `prefix_only` ENUM('0','1') NOT NULL DEFAULT '0'
COMMENT 'When 1, this word only applies to custom prefixes, not to chat/motto/guild.' AFTER `mute`;