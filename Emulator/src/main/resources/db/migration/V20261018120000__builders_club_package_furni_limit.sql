-- Builders Club membership packages raise the member furni limit: a month gives 1000, three months 1500.
-- The highest limit a user bought is kept here; 0 means the member default applies.
ALTER TABLE `users_settings` ADD COLUMN IF NOT EXISTS `builders_club_furni_limit` int(11) NOT NULL DEFAULT 0;
