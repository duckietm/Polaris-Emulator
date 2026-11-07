-- Habbi : le Game Center. Fast Food (un jeu Flash) ne se joue plus : il n'est plus listé. Les membres du Habbo Club ont
-- plus de parties gratuites de SnowStorm par jour. Idempotent.
UPDATE `emulator_settings` SET `value` = 'false' WHERE `key` = 'gamecenter.fastfood.enabled';
INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
  ('gamecenter.games.free.daily.hc', '20', 'Parties gratuites de SnowStorm par jour pour un membre du Habbo Club (jamais moins que gamecenter.games.free.daily ; -1 : illimité).');
