ALTER TABLE `chatlogs_private`
    DROP INDEX `message`,
    MODIFY `message` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    ADD INDEX `message` (`message`(191));

ALTER TABLE `chatlogs_room`
    MODIFY `message` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
