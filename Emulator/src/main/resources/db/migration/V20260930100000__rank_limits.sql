-- Limits a rank can raise: rooms, friends and favourite rooms. 0 keeps the hotel setting
-- (hotel.users.max.rooms[.hc], hotel.users.max.friends[.hc] / users_settings.max_friends,
-- hotel.rooms.max.favorite). A rank value never lowers a user below the hotel setting.
-- Idempotent; existing tools that read permission_ranks are unaffected.

ALTER TABLE `permission_ranks`
    ADD COLUMN IF NOT EXISTS `max_rooms` int(11) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `max_friends` int(11) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `max_favourite_rooms` int(11) NOT NULL DEFAULT 0;
