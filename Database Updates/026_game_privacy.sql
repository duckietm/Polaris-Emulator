ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `hide_online` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `block_friendrequests`;
