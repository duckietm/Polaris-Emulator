-- The mod tool's user info counts cautions in users_settings.cfh_warnings, a bare counter: nothing said who
-- cautioned the user, when, or why. Every caution that bumps the counter now also leaves a row here, so the mod
-- tool can list the cautions behind the number. Cautions given before this table existed have no row; the list
-- reports them as a count without detail. Additive and guarded: the statement is idempotent.

-- type is the action taken (ALERT, MUTE, BAN), length_hours the length of a mute or ban (0 when it has none),
-- created_at a unix timestamp in seconds.
CREATE TABLE IF NOT EXISTS `user_cautions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `staff_id` int(11) NOT NULL DEFAULT 0,
  `type` varchar(16) NOT NULL DEFAULT 'ALERT',
  `reason` varchar(255) NOT NULL DEFAULT '',
  `length_hours` int(11) NOT NULL DEFAULT 0,
  `created_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_cautions_user` (`user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
