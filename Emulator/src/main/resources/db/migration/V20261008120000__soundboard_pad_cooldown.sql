-- A pad can carry its own cooldown on top of the one a rank already has.
--
-- The value is the number of seconds one player waits before playing the same
-- pad again; 0 means the pad has no cooldown of its own, which is what every
-- existing row keeps.
--
-- Guarded through information_schema so a second run of the same patch is a
-- no-op, as in the other soundboard migrations.

SET @add_cooldown := (
    SELECT COUNT(*) FROM `information_schema`.`COLUMNS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'soundboard_sounds'
      AND `COLUMN_NAME` = 'cooldown_seconds');
SET @sql := IF(@add_cooldown = 0,
    'ALTER TABLE `soundboard_sounds` ADD COLUMN `cooldown_seconds` INT NOT NULL DEFAULT 0 AFTER `min_rank`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
