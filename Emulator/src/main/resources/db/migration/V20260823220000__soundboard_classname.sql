-- Soundboard pads stop being addressed by URL and start being addressed by
-- classname, the way furniture is.
--
-- The audio file and the pad's identity now live in the asset pipeline
-- (nitro-assets/sounds/soundboard + gamedata/SoundData.json); this table keeps
-- only what is hotel policy: enabled, ordering and the minimum rank.
--
-- `url` survives as an optional override for clips hosted outside the asset
-- tree (a CDN, say). A row needs a classname OR a url, never neither.
--
-- classname is NULLable on purpose: the unique index below has to tolerate
-- many url-only rows, and MySQL treats repeated NULLs as distinct while
-- rejecting repeated empty strings.

-- Idempotent by design: hotel owners sometimes run the same schema patch
-- twice. A bare `ADD COLUMN` / `CREATE UNIQUE INDEX` errors the second time
-- (Duplicate column / Duplicate key name), so both are guarded through
-- information_schema. `IF NOT EXISTS` is not used because MySQL 8 rejects it
-- on indexes (MariaDB-only); the information_schema + PREPARE pattern below
-- works on both engines.

SET @add_classname := (
    SELECT COUNT(*) FROM `information_schema`.`COLUMNS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'soundboard_sounds'
      AND `COLUMN_NAME` = 'classname');
SET @sql := IF(@add_classname = 0,
    'ALTER TABLE `soundboard_sounds` ADD COLUMN `classname` VARCHAR(64) NULL DEFAULT NULL AFTER `name`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill from the existing URLs: `/sounds/soundboard/campanella.mp3` -> `campanella`.
UPDATE `soundboard_sounds`
SET `classname` = LOWER(
        SUBSTRING_INDEX(
            SUBSTRING_INDEX(SUBSTRING_INDEX(`url`, '?', 1), '/', -1),
            '.', 1))
WHERE `classname` IS NULL
  AND `url` <> '';

-- Anything that did not yield a usable classname keeps its URL and is left
-- alone; the client falls back to the URL for those rows.
UPDATE `soundboard_sounds`
SET `classname` = NULL
WHERE `classname` IS NOT NULL
  AND `classname` NOT REGEXP '^[a-z0-9_-]{1,64}$';

-- Rows now resolved through the asset manifest no longer need the hardcoded
-- client-bundle path. Only clear the ones the backfill actually claimed.
UPDATE `soundboard_sounds`
SET `url` = ''
WHERE `classname` IS NOT NULL
  AND `url` LIKE '/sounds/soundboard/%';

SET @add_index := (
    SELECT COUNT(*) FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'soundboard_sounds'
      AND `INDEX_NAME` = 'idx_soundboard_sounds_classname');
SET @sql := IF(@add_index = 0,
    'CREATE UNIQUE INDEX `idx_soundboard_sounds_classname` ON `soundboard_sounds` (`classname`)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
