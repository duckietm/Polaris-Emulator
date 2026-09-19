-- Drop the indexes MariaDB reports on every fresh install as
-- "Duplicate index ... This is deprecated and will be disallowed in a future release".
-- Each one repeats an index the same table already carries, so no query loses a path.
-- The base dump is a released migration and stays untouched; this forward-fixes it.
ALTER TABLE `guilds_elements` DROP INDEX IF EXISTS `data`;
ALTER TABLE `room_enter_log` DROP INDEX IF EXISTS `room_id`;
ALTER TABLE `room_trade_log_items` DROP INDEX IF EXISTS `id_2`;
ALTER TABLE `room_trade_log_items` DROP INDEX IF EXISTS `id_3`;
ALTER TABLE `users_achievements_queue` DROP INDEX IF EXISTS `data`;
