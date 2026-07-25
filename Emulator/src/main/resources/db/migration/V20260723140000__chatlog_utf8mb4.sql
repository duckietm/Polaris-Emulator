-- Convert the chat log `message` columns to utf8mb4 so chat messages can store
-- the full Unicode range (emoji, etc.).
--
-- Both `message` columns carry a plain index. At utf8mb4 a full varchar(255)
-- key is 255 * 4 = 1020 bytes, which exceeds the MyISAM 1000-byte key limit, so
-- the index has to be dropped before the charset change and re-added afterwards
-- as a 191-char prefix (191 * 4 = 764 bytes, well under the limit).
--
-- Every index statement is guarded (DROP INDEX IF EXISTS / ADD INDEX IF NOT
-- EXISTS). The original upstream version used a bare `DROP INDEX \`message\``
-- and left `chatlogs_room`'s index in place, which aborts startup with
-- "Error 1091: Can't DROP INDEX \`message\`" when the index name differs, and
-- "Error 1071: Specified key was too long" when the 255-char index survives the
-- utf8mb4 conversion. These tables are MyISAM (non-transactional DDL cannot roll
-- back), so the guards also make the migration safe to re-run after a partial
-- failure.

ALTER TABLE `chatlogs_private` DROP INDEX IF EXISTS `message`;
ALTER TABLE `chatlogs_private`
    MODIFY `message` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `chatlogs_private` ADD INDEX IF NOT EXISTS `message` (`message`(191));

ALTER TABLE `chatlogs_room` DROP INDEX IF EXISTS `message`;
ALTER TABLE `chatlogs_room`
    MODIFY `message` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `chatlogs_room` ADD INDEX IF NOT EXISTS `message` (`message`(191));
