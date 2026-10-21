-- Optional: show the official Introduction reward track in game.
--
-- The migration V20260927120000__reward_track_introduction seeds the track enabled and
-- without a date window, so a fresh hotel shows it already. This script is for a hotel that
-- switched it off, gave it an end date that has passed, or a start date in the future.
-- It only touches the `introduction` row, never player progress, and is safe to run more
-- than once. Run :reloadrewards (or restart) afterwards so online users get the fresh list.
--
--   enabled = 1             the emulator loads only enabled tracks
--   starts_at = 0           no start date (or any unix time in the past)
--   ends_at = 0             no end date (or any unix time in the future)
--   sort_order = 0          first in the list, what the window opens on

UPDATE `reward_tracks`
SET `enabled` = 1,
    `starts_at` = IF(`starts_at` > UNIX_TIMESTAMP(), 0, `starts_at`),
    `ends_at` = IF(`ends_at` > 0 AND `ends_at` <= UNIX_TIMESTAMP(), 0, `ends_at`)
WHERE `id` = 'introduction'
  AND (`enabled` <> 1
       OR `starts_at` > UNIX_TIMESTAMP()
       OR (`ends_at` > 0 AND `ends_at` <= UNIX_TIMESTAMP()));

-- The demo track `season_1` (V20260907220000) runs next to it. The official client only
-- links the Introduction track from its progress menu; to keep just that one, remove the
-- leading "-- " of the next statement. Its rows and the progress of its players stay.
-- UPDATE `reward_tracks` SET `enabled` = 0 WHERE `id` = 'season_1' AND `enabled` <> 0;

-- What the hotel now loads: every enabled track and whether its date window is open.
SELECT `id`, `enabled`, `sort_order`, `starts_at`, `ends_at`,
       (`enabled` = 1
        AND (`starts_at` <= 0 OR `starts_at` <= UNIX_TIMESTAMP())
        AND (`ends_at` <= 0 OR UNIX_TIMESTAMP() < `ends_at`)) AS `visible`
FROM `reward_tracks`
ORDER BY `sort_order`, `id`;
