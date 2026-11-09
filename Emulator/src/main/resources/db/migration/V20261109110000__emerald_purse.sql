-- The purse shows the emerald balance (client: 1001 in system.currency.types). The login currency packet only sends
-- the types in seasonal.types, so the emerald type (hotel.collectibles.currency.emerald) is added to that list when it
-- is missing. Staff :points commands accept it from then on too. A hotel's own list is otherwise kept as it is.
UPDATE `emulator_settings` s
INNER JOIN (
    -- The emulator's default (1001) when the setting is missing or empty.
    SELECT COALESCE(
        (SELECT NULLIF(`value`, '') FROM `emulator_settings` WHERE `key` = 'hotel.collectibles.currency.emerald' LIMIT 1),
        '1001') AS `type`
) e
SET s.`value` = IF(s.`value` = '', e.`type`, CONCAT(s.`value`, ';', e.`type`))
WHERE s.`key` = 'seasonal.types'
  AND FIND_IN_SET(e.`type`, REPLACE(s.`value`, ';', ',')) = 0;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES ('seasonal.name.1001', 'Emeralds');
