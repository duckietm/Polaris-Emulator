-- How the avatar stands in the profile picture: the direction it faces (0-7, 2 as before) and how far it is moved
-- inside the 68 x 135 box, in pixels (-16 to 16 each way, 0 as before). Existing users keep today's look.
ALTER TABLE users
ADD COLUMN IF NOT EXISTS `profile_avatar_direction` TINYINT(1) NOT NULL DEFAULT 2 AFTER `profile_accent_id`,
ADD COLUMN IF NOT EXISTS `profile_avatar_offset_x` TINYINT(3) NOT NULL DEFAULT 0 AFTER `profile_avatar_direction`,
ADD COLUMN IF NOT EXISTS `profile_avatar_offset_y` TINYINT(3) NOT NULL DEFAULT 0 AFTER `profile_avatar_offset_x`;
