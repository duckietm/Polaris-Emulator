-- 021_furnidata_config_cleanup.sql
-- Reverts the emulator_settings rows inserted by 021_furnidata_config.sql.
--
-- Safe default:
-- This script ends with ROLLBACK. Run it once to preview the exact rows, then
-- change the final ROLLBACK to COMMIT only if the preview is correct.

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS cleanup_furnidata_settings;
CREATE TEMPORARY TABLE cleanup_furnidata_settings (
    `key` VARCHAR(255) NOT NULL PRIMARY KEY
);

INSERT INTO cleanup_furnidata_settings (`key`) VALUES
    ('items.furnidata.names.enabled'),
    ('items.furnidata.path'),
    ('items.furnidata.max.bytes'),
    ('items.furnidata.watch.enabled'),
    ('items.furnidata.watch.debounce.ms'),
    ('items.furnidata.watch.min.interval.ms'),
    ('items.furnidata.delta.cap'),
    ('furni.editor.import.url'),
    ('furni.editor.import.cache.ms');

-- Preview rows that will be removed.
SELECT es.`key`, es.`value`
FROM emulator_settings es
JOIN cleanup_furnidata_settings cfs ON cfs.`key` = es.`key`
ORDER BY es.`key`;

DELETE es
FROM emulator_settings es
JOIN cleanup_furnidata_settings cfs ON cfs.`key` = es.`key`;

-- Preview remaining matching rows inside the transaction.
SELECT COUNT(*) AS remaining_furnidata_settings
FROM emulator_settings es
JOIN cleanup_furnidata_settings cfs ON cfs.`key` = es.`key`;

-- Safe default. Change to COMMIT after reviewing the preview.
ROLLBACK;
