-- Per-room raid protection settings.
--
-- One row per configured room; a room without a row uses the defaults in
-- RaidProtectionSettings.defaults(). Every column mirrors a field the official client sends, except
-- incident_active, which answers "is a raid happening right now" and is runtime state rather than a
-- setting: it lives in RaidProtectionMonitor and is deliberately not persisted.
--
-- last_raid_at is epoch seconds, 0 for a room that has never been raided.

CREATE TABLE IF NOT EXISTS `room_raid_protection` (
    `room_id` INT(11) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 0,
    `detection_sensitivity` TINYINT(4) NOT NULL DEFAULT 1,
    `action_type` TINYINT(4) NOT NULL DEFAULT 0,
    `ban_duration_seconds` INT(11) NOT NULL DEFAULT 3600,
    `guard_enabled` TINYINT(1) NOT NULL DEFAULT 0,
    `guard_duration_seconds` INT(11) NOT NULL DEFAULT 1800,
    `guard_sensitivity` TINYINT(4) NOT NULL DEFAULT 2,
    `last_raid_at` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
