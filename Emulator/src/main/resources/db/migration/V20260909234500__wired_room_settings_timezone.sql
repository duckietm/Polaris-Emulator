-- AIR 13 wired settings tab: the permissions packet (1936) carries a timezone next to the
-- read/modify permission masks, and the settings answer sends it back so the picker can be
-- restored. It is stored beside the masks the same packet already persists.
ALTER TABLE `room_wired_settings`
    ADD COLUMN IF NOT EXISTS `timezone` VARCHAR(64) NOT NULL DEFAULT '';
