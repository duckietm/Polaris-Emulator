-- Kick User exceptions read as in Habbo ("Wired kick exception: Room owner") and are whispered in the
-- Wired bubble. The WHERE keeps a text a hotel already reworded.
UPDATE `emulator_texts`
SET `value` = 'Wired kick exception: Room owner'
WHERE `key` = 'hotel.wired.kickexception.owner'
	AND `value` = 'Wired Kick Exception: Room Owner';

UPDATE `emulator_texts`
SET `value` = 'Wired kick exception: Unkickable'
WHERE `key` = 'hotel.wired.kickexception.unkickable'
	AND `value` = 'Wired Kick Exception: Unkickable';
