-- When a room was created, and the competition rule that needs it.
--
-- The official room competition refuses a room that is "too old to enter": the
-- point is that people build something new for the contest instead of entering
-- a room they already had. Nothing recorded when a room was made, so the column
-- starts at zero for every room that already exists and is filled from now on.
ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `date_created` INT NOT NULL DEFAULT 0;

-- Zero leaves the rule off, which is how every competition behaves until the
-- hotel sets a date. With a date set, only rooms created after it may enter, and
-- a room whose creation date is unknown (the zero above) counts as too old:
-- there is no way to prove it was built for this competition.
ALTER TABLE `room_competitions` ADD COLUMN IF NOT EXISTS `rooms_created_after` INT NOT NULL DEFAULT 0;
