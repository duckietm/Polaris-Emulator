-- Two pieces of per-user state the official AIR 13 client expects the server to own.
--
-- 1) The account safety lock: `UserInfo` carries an `accountSafetyLocked` flag (last boolean of
--    the packet) and `AccountSafetyLockStatusChange` (1243) tells the client when the lock is
--    turned on (status 0 = locked) or released (status 1 = unlocked). The emulator hardcoded the
--    flag to false and never sent the status change, so the toolbar badge could never appear.
--
-- 2) The mod tool window geometry: `ModToolPreferences` (31) stores where the moderator dragged
--    and resized the issue handler, and the server plays it back with
--    `ModToolIssueHandlerDimensionsComposer` (1576) on the next login.

ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `safety_locked` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `respects_received`,
    ADD COLUMN IF NOT EXISTS `modtool_window_x` INT NOT NULL DEFAULT 0 AFTER `safety_locked`,
    ADD COLUMN IF NOT EXISTS `modtool_window_y` INT NOT NULL DEFAULT 0 AFTER `modtool_window_x`,
    ADD COLUMN IF NOT EXISTS `modtool_window_width` INT NOT NULL DEFAULT 0 AFTER `modtool_window_y`,
    ADD COLUMN IF NOT EXISTS `modtool_window_height` INT NOT NULL DEFAULT 0 AFTER `modtool_window_width`;
