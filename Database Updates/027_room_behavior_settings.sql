ALTER TABLE `rooms`
    ADD COLUMN IF NOT EXISTS `mute_all_pets` TINYINT(1) NOT NULL DEFAULT 0 AFTER `allow_other_pets_eat`,
    ADD COLUMN IF NOT EXISTS `leave_on_door_tile` TINYINT(1) NOT NULL DEFAULT 1 AFTER `allow_walkthrough`,
    ADD COLUMN IF NOT EXISTS `idle_sleep_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `allow_underpass`,
    ADD COLUMN IF NOT EXISTS `idle_sleep_timeout_seconds` INT NOT NULL DEFAULT 0 AFTER `idle_sleep_enabled`,
    ADD COLUMN IF NOT EXISTS `idle_autokick_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `idle_sleep_timeout_seconds`,
    ADD COLUMN IF NOT EXISTS `idle_autokick_timeout_seconds` INT NOT NULL DEFAULT 0 AFTER `idle_autokick_enabled`;
