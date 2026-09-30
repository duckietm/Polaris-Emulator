-- A user's own permission values, checked before the rank and the plugins: a timed sanction
-- (a key set to 0 for 7 days) or a grant (an event host's key for a day). expires_at 0 is
-- permanent; an expired row is ignored. Managed with :perm set / unset / list. Idempotent.

CREATE TABLE IF NOT EXISTS `user_permission_overrides` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `permission_key` varchar(64) NOT NULL,
  `value` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `expires_at` int(11) NOT NULL DEFAULT 0,
  `reason` varchar(255) NOT NULL DEFAULT '',
  `created_by` int(11) NOT NULL DEFAULT 0,
  `created_by_name` varchar(64) NOT NULL DEFAULT '',
  `created_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_key` (`user_id`, `permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_perm', ':perm check|log|list|set|unset')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
