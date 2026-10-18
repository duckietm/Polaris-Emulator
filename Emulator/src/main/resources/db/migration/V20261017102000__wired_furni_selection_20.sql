-- Every official wired editor frame lets a box pick up to 20 furni. Only hotels still on the old
-- default of 5 are raised; a value someone chose stays, and boxes already holding more keep loading.
UPDATE `wired_emulator_settings`
SET `value` = '20',
    `comment` = 'Maximum number of furni that a wired box can store or select (Habbo: 20).'
WHERE `key` = 'hotel.wired.furni.selection.count'
  AND `value` = '5';

UPDATE `emulator_settings`
SET `value` = '20',
    `comment` = 'Maximum number of furni that a wired box can store or select (Habbo: 20).'
WHERE `key` = 'hotel.wired.furni.selection.count'
  AND `value` = '5';
