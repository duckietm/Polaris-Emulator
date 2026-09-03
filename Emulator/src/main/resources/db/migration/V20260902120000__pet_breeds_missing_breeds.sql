-- Several pets ship body-colour palettes in their .nitro bundle that were never
-- registered in pet_breeds, so their breed selectors offered fewer colours than
-- the assets support. pet_breeds.color_one is the palette id as numbered in the
-- pet's .nitro bundle. Every id below is a non-master body palette with a valid,
-- distinct colour ramp.
--
--   Spider     (race 8)  + ids 12,13,15,16,17           13 -> 18 breeds
--   Turtle     (race 9)  + ids 9,10,11                    9 -> 12 breeds
--   Frog       (race 11) + ids 7,14,16,17                14 -> 18 breeds
--                          (elvis, darkness, brightgold, bollywood)
--   Monkey     (race 14) + id 14 (monkey15)              14 -> 15 breeds
--   Gnome      (race 26) + ids 1,6,7,28,29,30 (body1..6)  1 -> 7  breeds
--   Leprechaun (race 27) + ids 1,6,7,28,29,30             1 -> 7  breeds
--   Cow        (race 35) + ids 19,29,31,32               16 -> 20 breeds
--                          (arctic, devil, fantasy, crossbred; golden stays a
--                          Gold Box exclusive)
--
-- has_color_one/has_color_two follow each race's own convention: spider, turtle,
-- frog, cow use '1','0' like their existing rows; monkey is uniformly two-tone so
-- id 14 uses '1','1' to match; gnome and leprechaun previously held only an
-- anomalous ('0','0') base row, so their real colour breeds use the standard
-- '1','0' (as dog/cat/etc. do). color_two mirrors color_one.
--
-- The pet_breeds UNIQUE KEY (race, color_one, color_two, has_color_one,
-- has_color_two) makes INSERT IGNORE idempotent and preserves any row a hotel
-- already added, so this migration is safe to replay.
INSERT IGNORE INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`) VALUES
    (8, 12, 12, '1', '0'),
    (8, 13, 13, '1', '0'),
    (8, 15, 15, '1', '0'),
    (8, 16, 16, '1', '0'),
    (8, 17, 17, '1', '0'),
    (9, 9, 9, '1', '0'),
    (9, 10, 10, '1', '0'),
    (9, 11, 11, '1', '0'),
    (11, 7, 7, '1', '0'),
    (11, 14, 14, '1', '0'),
    (11, 16, 16, '1', '0'),
    (11, 17, 17, '1', '0'),
    (14, 14, 14, '1', '1'),
    (26, 1, 1, '1', '0'),
    (26, 6, 6, '1', '0'),
    (26, 7, 7, '1', '0'),
    (26, 28, 28, '1', '0'),
    (26, 29, 29, '1', '0'),
    (26, 30, 30, '1', '0'),
    (27, 1, 1, '1', '0'),
    (27, 6, 6, '1', '0'),
    (27, 7, 7, '1', '0'),
    (27, 28, 28, '1', '0'),
    (27, 29, 29, '1', '0'),
    (27, 30, 30, '1', '0'),
    (35, 19, 19, '1', '0'),
    (35, 29, 29, '1', '0'),
    (35, 31, 31, '1', '0'),
    (35, 32, 32, '1', '0');
