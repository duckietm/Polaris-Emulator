-- Room privacy / idle-sleep / pet-mute + user "hide online" columns.
--
-- These columns were introduced with the hotel-view/privacy feature in
-- V20260723120000__hotelview_landing_and_privacy.sql. This follow-up migration
-- re-asserts them at a higher version so databases where the original
-- migration did not take effect still get the columns instead of crashing on
-- boot in RoomSnapshot.complete with "Unknown label 'mute_all_pets'".
--
-- Every statement uses ADD COLUMN IF NOT EXISTS, so this is a no-op where the
-- earlier migration already applied.

ALTER TABLE `rooms`
    ADD COLUMN IF NOT EXISTS `mute_all_pets` TINYINT(1) NOT NULL DEFAULT 0 AFTER `allow_other_pets_eat`,
    ADD COLUMN IF NOT EXISTS `leave_on_door_tile` TINYINT(1) NOT NULL DEFAULT 1 AFTER `allow_walkthrough`,
    ADD COLUMN IF NOT EXISTS `idle_sleep_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `allow_underpass`,
    ADD COLUMN IF NOT EXISTS `idle_sleep_timeout_seconds` INT NOT NULL DEFAULT 0 AFTER `idle_sleep_enabled`,
    ADD COLUMN IF NOT EXISTS `idle_autokick_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `idle_sleep_timeout_seconds`,
    ADD COLUMN IF NOT EXISTS `idle_autokick_timeout_seconds` INT NOT NULL DEFAULT 0 AFTER `idle_autokick_enabled`;

ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `hide_online` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `block_friendrequests`;
