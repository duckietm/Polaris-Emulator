-- A user can hide their extended profile from other users.
--
-- The official extended profile carries an isHidden flag: when it is set, everybody but the
-- owner sees "profile.full_profile_hidden" in place of the groups section. Official Habbo set
-- it from the website; here it is a game privacy setting next to hide_online.
--
-- Additive and idempotent: existing users keep a visible profile.

ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `hide_profile` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `hide_online`;
