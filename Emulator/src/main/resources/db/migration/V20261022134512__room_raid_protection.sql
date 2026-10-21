-- AIR raid protection (navigator/raidprotection): the owner's per-room settings, and one row per detected raid.
-- A room without a settings row uses the defaults below (protection off). Additive and guarded: a hotel that
-- already carries the tables keeps them as they are.
CREATE TABLE IF NOT EXISTS `room_raid_protection` (
  `room_id` int(11) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `sensitivity` tinyint(4) NOT NULL DEFAULT 1,
  `action_type` tinyint(4) NOT NULL DEFAULT 0,
  `ban_duration` int(11) NOT NULL DEFAULT 3600,
  `guard_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `guard_duration` int(11) NOT NULL DEFAULT 900,
  `guard_sensitivity` tinyint(4) NOT NULL DEFAULT 1,
  `last_raid_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- protection_enabled 0 is a shadow incident: detected while protection was off, reported to the owner only.
CREATE TABLE IF NOT EXISTS `room_raid_incidents` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `room_id` int(11) NOT NULL,
  `protection_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `sensitivity` tinyint(4) NOT NULL DEFAULT 1,
  `action_type` tinyint(4) NOT NULL DEFAULT 0,
  `trigger_type` varchar(16) NOT NULL DEFAULT 'entry',
  `started_at` int(11) NOT NULL,
  `ended_at` int(11) NOT NULL DEFAULT 0,
  `participants` int(11) NOT NULL DEFAULT 0,
  `actioned_users` int(11) NOT NULL DEFAULT 0,
  `blocked_entries` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_room_raid_incidents_room` (`room_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.raid.protection.enabled', '1', 'Raid protection (AIR room info button): owners can protect a room against bot raids; 0 hides it and stops detection.'),
    ('hotel.raid.protection.new_account_days', '7', 'Raid protection: an account younger than this many days counts as unknown when it enters a room.'),
    ('hotel.raid.protection.quiet_seconds', '120', 'Raid protection: a raid ends after this many seconds without suspicious entries or chat.')
ON DUPLICATE KEY UPDATE `value` = `value`;
