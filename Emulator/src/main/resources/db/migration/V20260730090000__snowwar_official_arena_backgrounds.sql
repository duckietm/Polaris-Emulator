-- The original defaults referenced a dead local web-root. Replace only those
-- untouched values; hotel-owner overrides remain unchanged.
UPDATE `emulator_settings`
SET `value` = 'https://images.habbo.com/c_images/DEV_tests/snst_bg_1_a_big.png'
WHERE `key` = 'gamecenter.snowwar.artic.bg'
  AND `value` IN (
      'http://localhost/swf/c_images/gamecenter_snowwar/snst_bg_1_a_big.png',
      'http://127.0.0.1:8081/swf/c_images/gamecenter_snowwar/snst_bg_1_a_big.png'
  );

UPDATE `emulator_settings`
SET `value` = 'https://images.habbo.com/c_images/DEV_tests/snst_bg_2_big.png'
WHERE `key` = 'gamecenter.snowwar.dragoncave.bg'
  AND `value` IN (
      'http://localhost/swf/c_images/gamecenter_snowwar/snst_bg_2_big.png',
      'http://127.0.0.1:8081/swf/c_images/gamecenter_snowwar/snst_bg_2_big.png'
  );

UPDATE `emulator_settings`
SET `value` = 'https://images.habbo.com/c_images/DEV_tests/snst_bg_3_noscale.png'
WHERE `key` = 'gamecenter.snowwar.fightnight.bg'
  AND `value` IN (
      'http://localhost/swf/c_images/gamecenter_snowwar/snst_bg_3_noscale.png',
      'http://127.0.0.1:8081/swf/c_images/gamecenter_snowwar/snst_bg_3_noscale.png'
  );
