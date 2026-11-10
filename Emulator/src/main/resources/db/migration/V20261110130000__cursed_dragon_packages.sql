-- The furni side of the cursed dragon (pet type 37).
--
-- `a0 pet37` is the item a catalogue pets page sells the dragon with. It is added by name only when missing, with a
-- new id: a hotel that already has it keeps its own. The 0/1 flags are quoted for the enum('0','1') columns of
-- Arcturus-derived hotels.
INSERT INTO `items_base`
    (`sprite_id`, `public_name`, `item_name`, `type`, `width`, `length`, `stack_height`,
     `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`, `allow_gift`, `allow_trade`,
     `allow_recycle`, `allow_marketplace_sell`, `allow_inventory_stack`, `interaction_type`,
     `interaction_modes_count`, `vending_ids`, `multiheight`, `customparams`)
SELECT 0, 'a0 pet37', 'a0 pet37', 's', 0, 0, 0.00,
       '1', '0', '0', '0', '1', '1', '0', '0', '1', 'default', 1, '0', '', ''
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `items_base` WHERE `item_name` = 'a0 pet37');

-- What the dragon adoption gives: the first choice (0, the blue dragon) the Diamond Dragon Lamp, the second (1, the
-- purple dragon) the Frost Dragon lamp. Looked up by item name, so a hotel without one of the furni gets no row for
-- that choice, and a hotel that already set its own choices for the claim keeps them.
INSERT INTO `special_items_free_claim_choices` (`claim_id`, `choice`, `item_id`)
SELECT 'cursed_dragon_adoption', `reward`.`choice`, `reward`.`id`
FROM (
    SELECT 0 AS `choice`, MIN(`id`) AS `id` FROM `items_base` WHERE `item_name` = 'diamond_dragon'
    UNION ALL
    SELECT 1, MIN(`id`) FROM `items_base` WHERE `item_name` = 'rare_dragonlamp*9'
) `reward`
WHERE `reward`.`id` IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT `claim_id` FROM `special_items_free_claim_choices`) `existing`
      WHERE `existing`.`claim_id` = 'cursed_dragon_adoption');
