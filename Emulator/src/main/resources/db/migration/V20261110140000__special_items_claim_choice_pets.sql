-- A choice of a free claim may give a pet, besides or instead of its furni: adopting a dragon should put the dragon
-- in the pets inventory. pet_type is the pet (pet_actions.pet_type; -1 for none), pet_breed its colour (the palette,
-- pet_breeds.color_one) and pet_name the name it gets.
ALTER TABLE `special_items_free_claim_choices`
    ADD COLUMN IF NOT EXISTS `pet_type` int(11) NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `pet_breed` int(11) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `pet_name` varchar(32) NOT NULL DEFAULT '';

-- The dragon adoption: the first choice gives the blue cursed dragon (pet 37, breed 0), the second the purple one
-- (breed 1), whatever furni the hotel set for the choice. Only choices that give no pet yet are changed.
UPDATE `special_items_free_claim_choices`
SET `pet_type` = 37, `pet_breed` = 0, `pet_name` = 'Blue Dragon'
WHERE `claim_id` = 'cursed_dragon_adoption' AND `choice` = 0 AND `pet_type` = -1;

UPDATE `special_items_free_claim_choices`
SET `pet_type` = 37, `pet_breed` = 1, `pet_name` = 'Purple Dragon'
WHERE `claim_id` = 'cursed_dragon_adoption' AND `choice` = 1 AND `pet_type` = -1;
