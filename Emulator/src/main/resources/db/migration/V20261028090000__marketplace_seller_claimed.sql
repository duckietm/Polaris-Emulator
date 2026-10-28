-- A sold marketplace offer was marked as paid out by setting its user_id to -1. Databases with the fk_market_user
-- foreign key (marketplace_items.user_id -> users.id) refuse that update, so collecting marketplace credits failed
-- and the seller was never paid. The offer now keeps its seller and is marked paid out with this flag instead.
-- Offers already paid out the old way keep user_id -1 and stay hidden from their seller as before.
ALTER TABLE `marketplace_items` ADD COLUMN IF NOT EXISTS `seller_claimed` TINYINT(1) NOT NULL DEFAULT 0;
