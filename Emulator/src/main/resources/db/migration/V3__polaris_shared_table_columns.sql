-- V3: columns Polaris adds to tables shared with Arcturus 3.5.5.
-- ADD COLUMN IF NOT EXISTS (MariaDB) so an install that already has the column is a no-op.
-- NOTE: this guards on column NAME. Where a converter might hold a DIFFERENT definition,
-- a state-aware check (see the authoring guide) should replace the plain guard.

-- The supplied Arc dump uses MySQL 8's utf8mb4_0900_ai_ci for four tables.
-- MariaDB 10.11 does not provide that collation. Normalize both fresh installs
-- and converters to the MariaDB-supported utf8mb4_unicode_ci without changing
-- the calendar_rewards columns that were explicitly declared latin1.
ALTER TABLE `calendar_campaigns`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
    MODIFY `image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
    MODIFY `lock_expired` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1',
    MODIFY `enabled` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1';
ALTER TABLE `calendar_rewards`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `calendar_rewards_claimed`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `messenger_categories`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY `name` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

ALTER TABLE `bot_serves` ADD COLUMN IF NOT EXISTS `id` int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE `catalog_pages` ADD COLUMN IF NOT EXISTS `catalog_mode` enum('NORMAL','BUILDER','BOTH') NOT NULL DEFAULT 'NORMAL';
ALTER TABLE `chatlogs_room` ADD COLUMN IF NOT EXISTS `id` int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE `commandlogs` ADD COLUMN IF NOT EXISTS `id` int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE `emulator_settings` ADD COLUMN IF NOT EXISTS `comment` text DEFAULT '';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `hidden_rank` tinyint(1) NOT NULL DEFAULT 0;
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `job_description` varchar(255) NOT NULL DEFAULT 'Here to help';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `staff_color` varchar(8) NOT NULL DEFAULT '#327fa8';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `staff_background` varchar(255) NOT NULL DEFAULT 'staff-bg.png';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cms_dance` enum('0','1') DEFAULT '0';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_update_all` enum('0','1') NOT NULL DEFAULT '0';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `acc_catalogfurni` enum('0','1') NOT NULL DEFAULT '0';
ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `allow_underpass` enum('0','1') NOT NULL DEFAULT '0';
ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `builders_club_trial_locked` tinyint(1) NOT NULL DEFAULT 0;
ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `builders_club_original_state` varchar(16) NOT NULL DEFAULT 'open';
ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `youtube_enabled` tinyint(1) NOT NULL DEFAULT 0;
ALTER TABLE `room_enter_log` ADD COLUMN IF NOT EXISTS `id` int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `auth_ticket_expires_at` timestamp NULL DEFAULT NULL;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `remember_token_hash` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '';
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `remember_token_expires_at` int(11) unsigned NOT NULL DEFAULT 0;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `background_id` int(11) NOT NULL DEFAULT 0;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `background_stand_id` int(11) NOT NULL DEFAULT 0;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `background_overlay_id` int(11) NOT NULL DEFAULT 0;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `background_card_id` int(11) NOT NULL DEFAULT 0;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `last_username_change` int(11) NOT NULL DEFAULT 0;
ALTER TABLE `users_settings` ADD COLUMN IF NOT EXISTS `builders_club_bonus_furni` int(11) NOT NULL DEFAULT 0;
