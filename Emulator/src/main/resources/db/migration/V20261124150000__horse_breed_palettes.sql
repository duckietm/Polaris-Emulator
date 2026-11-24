-- The horse rows of pet_breeds (race 15) file a body palette (color_two) under a breed (color_one). The horse
-- figure has four palettes per coat colour, one per breed: colours 1-12 are palettes 2-49, colours 13-17 are 61-80
-- (50-60 are hair palettes). The default rows are right for colours 1-5 only: from colour 6 on every palette is one
-- too low, so each breed offered the previous breed's marking (and breed 1 of colour 6 was colour 5 again), and
-- colours 13-15 pointed at hair palettes.
--
-- Only rows that carry exactly that mistake are moved: a row whose palette already belongs to its breed, and any
-- row a hotel added itself, stays as it is. Replaying changes nothing, and no horse a player owns is touched.

-- Colours 13-15, listed at 49-60: twelve higher.
UPDATE IGNORE `pet_breeds`
SET `color_two` = `color_two` + 12
WHERE `race` = 15
  AND `color_two` BETWEEN 49 AND 60
  AND ((`color_two` - 49) % 4) + 1 = `color_one`;

-- Colours 6-12, listed one palette too low.
UPDATE IGNORE `pet_breeds`
SET `color_two` = `color_two` + 1
WHERE `race` = 15
  AND `color_two` BETWEEN 21 AND 48
  AND ((`color_two` - 1) % 4) + 1 = `color_one`;
