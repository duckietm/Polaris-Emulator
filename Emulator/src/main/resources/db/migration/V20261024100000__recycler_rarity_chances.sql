-- Recycler (Furnimatic) prize odds, "1 in N" per rarity level. The emulator tries level 5 first, then 4, 3 and 2;
-- whatever is left is level 1 (common). These values give about 0.25% / 2.5% / 9.7% / 21.9% / 65.7%.
-- Setup and limits: docs/discord/recycler-setup.md.

UPDATE `emulator_settings` SET `value` = '400' WHERE `key` = 'hotel.ecotron.rarity.chance.5';
UPDATE `emulator_settings` SET `value` = '40'  WHERE `key` = 'hotel.ecotron.rarity.chance.4';
UPDATE `emulator_settings` SET `value` = '10'  WHERE `key` = 'hotel.ecotron.rarity.chance.3';
UPDATE `emulator_settings` SET `value` = '4'   WHERE `key` = 'hotel.ecotron.rarity.chance.2';

UPDATE `emulator_settings` SET `comment` = 'Recycler: 1 in N chance of a level 5 (legendary) prize, tried first.' WHERE `key` = 'hotel.ecotron.rarity.chance.5';
UPDATE `emulator_settings` SET `comment` = 'Recycler: 1 in N chance of a level 4 prize, tried when level 5 missed.' WHERE `key` = 'hotel.ecotron.rarity.chance.4';
UPDATE `emulator_settings` SET `comment` = 'Recycler: 1 in N chance of a level 3 prize, tried when levels 5-4 missed.' WHERE `key` = 'hotel.ecotron.rarity.chance.3';
UPDATE `emulator_settings` SET `comment` = 'Recycler: 1 in N chance of a level 2 prize, tried when levels 5-3 missed; otherwise level 1.' WHERE `key` = 'hotel.ecotron.rarity.chance.2';
