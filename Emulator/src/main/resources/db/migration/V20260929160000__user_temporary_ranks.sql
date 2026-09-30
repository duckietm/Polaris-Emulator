-- Ranks given for a while (:perm rank <user> <rank> <duration>). When expires_at passes, the
-- emulator gives the user previous_rank_id back (checked once a minute, online or offline).
-- Any other rank change removes the row. Idempotent.

CREATE TABLE IF NOT EXISTS `user_temporary_ranks` (
  `user_id` int(11) NOT NULL,
  `rank_id` int(11) NOT NULL,
  `previous_rank_id` int(11) NOT NULL,
  `expires_at` int(11) NOT NULL,
  `set_by` int(11) NOT NULL DEFAULT 0,
  `set_by_name` varchar(64) NOT NULL DEFAULT '',
  `reason` varchar(255) NOT NULL DEFAULT '',
  `created_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`),
  KEY `expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_perm', ':perm check|log|list|set|unset|rank')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
