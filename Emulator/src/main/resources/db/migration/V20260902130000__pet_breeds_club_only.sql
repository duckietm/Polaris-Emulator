-- Per-breed HC (Habbo Club) gate for sellable pet breeds. When club_only = '1',
-- only members with active HC may select and buy that breed: it is still sent to
-- every client (shown with an HC lock), but the server blocks the purchase for
-- non-HC users. Default '0' keeps every existing breed open to everyone.
-- ADD COLUMN IF NOT EXISTS (MariaDB) so a hotel that already has the column is a no-op.
ALTER TABLE `pet_breeds` ADD COLUMN IF NOT EXISTS `club_only` enum('0','1') NOT NULL DEFAULT '0';
