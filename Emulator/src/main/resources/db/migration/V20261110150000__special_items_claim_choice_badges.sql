-- A choice of a free claim may give a badge. The official dragon adoption gives PET17 ("I adopted a Blue Cursed
-- Dragon!") with the blue dragon and PET18 ("I adopted a Purple Cursed Dragon!") with the purple one.
ALTER TABLE `special_items_free_claim_choices`
    ADD COLUMN IF NOT EXISTS `badge_code` varchar(32) NOT NULL DEFAULT '';

-- Only choices that give no badge yet are changed.
UPDATE `special_items_free_claim_choices`
SET `badge_code` = 'PET17'
WHERE `claim_id` = 'cursed_dragon_adoption' AND `choice` = 0 AND `badge_code` = '';

UPDATE `special_items_free_claim_choices`
SET `badge_code` = 'PET18'
WHERE `claim_id` = 'cursed_dragon_adoption' AND `choice` = 1 AND `badge_code` = '';
