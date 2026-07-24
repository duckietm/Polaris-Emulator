ALTER TABLE `chatlogs_private` DROP INDEX IF EXISTS `message`;
ALTER TABLE `chatlogs_private`
    MODIFY `message` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `chatlogs_private` ADD INDEX IF NOT EXISTS `message` (`message`(191));

ALTER TABLE `chatlogs_room` DROP INDEX IF EXISTS `message`;
ALTER TABLE `chatlogs_room`
    MODIFY `message` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `chatlogs_room` ADD INDEX IF NOT EXISTS `message` (`message`(191));