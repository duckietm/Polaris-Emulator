-- Rentable bot menu live updates (AIR BotSkillListUpdate + BotForceOpenContextMenu): after an owner
-- uses dress up, random walk or dance, the server sends the bot's skills and reopens its menu, so the
-- owner can keep using it. 0 keeps the menu closed as before.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.bots.menu.reopen_after_action', '1', 'Reopen a rentable bot''s menu after its owner uses dress up, random walk or dance (1 = on, 0 = off).')
ON DUPLICATE KEY UPDATE `value` = `value`;
