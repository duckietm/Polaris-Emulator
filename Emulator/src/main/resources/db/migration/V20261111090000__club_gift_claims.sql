-- The history of chosen club gifts: who took which gift and when. The number of gifts a member may still take stays
-- in users_settings.hc_gifts_claimed; this table is the record behind it, for staff and housekeeping tools. Gifts
-- taken before this table existed are not in it.
CREATE TABLE IF NOT EXISTS `club_gift_claims` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `user_id` int(11) NOT NULL,
    `catalog_item_id` int(11) NOT NULL,
    `catalog_name` varchar(100) NOT NULL DEFAULT '',
    `claimed_at` int(11) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_club_gift_claims_user` (`user_id`, `claimed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
