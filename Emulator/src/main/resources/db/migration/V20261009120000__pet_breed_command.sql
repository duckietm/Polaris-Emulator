-- Breeding starts with the pet command 46 ("Breed"), which walks the pet to a free breeding nest.
-- No breedable pet type had that command, so breeding could never start. Give it to every type
-- that can breed (pet_breeding); rows a hotel already has are left alone. Idempotent.

INSERT INTO `pet_commands` (`pet_id`, `command_id`)
SELECT DISTINCT b.`pet_id`, 46
FROM `pet_breeding` b
WHERE NOT EXISTS (
    SELECT 1 FROM `pet_commands` c WHERE c.`pet_id` = b.`pet_id` AND c.`command_id` = 46
);
