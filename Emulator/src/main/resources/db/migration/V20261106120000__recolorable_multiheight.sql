-- Recolourable furni (interaction_type 'recolorable') take the heights of the multiheight furni they recolour:
-- bc_cylinder and bc_cylinder_recolorable share their states, so the recolourable one gets the multiheight list,
-- state count and stack height of its multiheight twin. Looked up by item_name, so the ids of each hotel's
-- items_base do not matter. A recolourable furni without a multiheight twin is left alone, and a twin without a
-- multiheight list changes nothing.

UPDATE `items_base` AS `r`
JOIN `items_base` AS `n`
    ON `n`.`item_name` = LEFT(`r`.`item_name`, CHAR_LENGTH(`r`.`item_name`) - CHAR_LENGTH('_recolorable'))
SET `r`.`multiheight` = `n`.`multiheight`,
    `r`.`interaction_modes_count` = `n`.`interaction_modes_count`,
    `r`.`stack_height` = `n`.`stack_height`
WHERE `r`.`interaction_type` = 'recolorable'
  AND `r`.`item_name` LIKE '%\_recolorable'
  AND `n`.`interaction_type` = 'multiheight'
  AND `n`.`multiheight` NOT IN ('', '0');
