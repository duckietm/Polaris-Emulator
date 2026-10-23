-- Recycler (Furnimatic) level 6, mythic: tried before level 5 with a 1 in 5000 chance (about 0.02%). Give it prizes in
-- recycler_prizes with rarity = 6; a level without prizes makes the recycle fail, so set the chance to 0 to leave
-- the level out. Setup: docs/discord/recycler-setup.md.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.ecotron.rarity.chance.6', '5000', 'Recycler: 1 in N chance of a level 6 (mythic) prize, tried first; 0 = no level 6.')
ON DUPLICATE KEY UPDATE `value` = `value`;

UPDATE `emulator_settings` SET `comment` = 'Recycler: 1 in N chance of a level 5 (legendary) prize, tried when level 6 missed.' WHERE `key` = 'hotel.ecotron.rarity.chance.5';
