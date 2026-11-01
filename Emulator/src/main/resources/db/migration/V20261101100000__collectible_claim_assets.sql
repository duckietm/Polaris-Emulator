-- The tokens that earned each collectible claim. Claims were kept per user only, so a token passed to another
-- account earned the same set or level reward again; a token recorded here no longer counts for that claim on any
-- other account.
CREATE TABLE IF NOT EXISTS `collectible_claim_assets` (
  `claim_key` varchar(96) NOT NULL,
  `asset_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL,
  `claimed_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`claim_key`, `asset_id`),
  KEY `idx_collectible_claim_assets_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
