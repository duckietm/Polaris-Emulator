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

ALTER TABLE `soundboard_sounds`
    ADD COLUMN `classname` VARCHAR(64) NULL DEFAULT NULL AFTER `name`;

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

CREATE UNIQUE INDEX `idx_soundboard_sounds_classname`
    ON `soundboard_sounds` (`classname`);
