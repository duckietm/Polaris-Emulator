-- The cursed dragon (pet type 37, asset `curseddragon`): the pet the official client draws on the crates of its
-- dragon adoption window. The bundle has three body palettes: 0 blue, 1 purple, 2 green. It moves like the dragon
-- (type 12, also flame and eat), so it gets the dragon's commands.
--
-- Every statement leaves an existing row alone: a hotel that already added the pet keeps its own name, breeds and
-- commands.
INSERT INTO `pet_actions` (`pet_type`, `pet_name`, `offspring_type`, `happy_actions`, `tired_actions`, `random_actions`, `can_swim`)
SELECT 37, 'Cursed Dragon', -1, '', '', '', '0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `pet_actions` WHERE `pet_type` = 37);

INSERT IGNORE INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`) VALUES
    (37, 0, 0, '1', '0'),
    (37, 1, 1, '1', '0'),
    (37, 2, 2, '1', '0');

INSERT INTO `pet_commands` (`pet_id`, `command_id`)
SELECT 37, `dragon`.`command_id`
FROM `pet_commands` `dragon`
WHERE `dragon`.`pet_id` = 12
  AND NOT EXISTS (SELECT 1 FROM (SELECT `pet_id` FROM `pet_commands`) `existing` WHERE `existing`.`pet_id` = 37);
