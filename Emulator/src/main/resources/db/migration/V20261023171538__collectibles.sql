-- AIR collectibles (Collectors Guild hub, inventory collectibles tab, reward boxes, relics) without a
-- blockchain: every collectible is a token owned by a user in this database. See docs/collectibles.md.
-- Additive and guarded: a hotel that already carries the tables keeps them as they are. The feature is
-- off until an operator sets hotel.collectibles.enabled to 1.

-- What can be collected. product_type is the official client's product type (0 wall item, 1 floor item,
-- 2 effect, 4 badge, 9 chat style, 10 pet, 11 clothing); item_type_id is the items_base id (0, 1, 11),
-- effect id, badge code, chat style id or pet type. score is the collector XP the product is worth.
-- emerald_price with shop_enabled 1 sells it in the guild shop; mint_limit caps the serials (-1 none).
CREATE TABLE IF NOT EXISTS `collectible_products` (
  `product_code` varchar(64) NOT NULL,
  `product_type` smallint(6) NOT NULL DEFAULT 1,
  `item_type_id` varchar(64) NOT NULL DEFAULT '',
  `score` int(11) NOT NULL DEFAULT 0,
  `rarity` varchar(16) NOT NULL DEFAULT 'common',
  `pet_figure` varchar(64) NOT NULL DEFAULT '',
  `figure_set_ids` varchar(255) NOT NULL DEFAULT '',
  `collection_id` varchar(64) DEFAULT NULL,
  `emerald_price` int(11) NOT NULL DEFAULT 0,
  `shop_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `mint_limit` int(11) NOT NULL DEFAULT -1,
  `minted_count` int(11) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`product_code`),
  KEY `idx_collectible_products_collection` (`collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sets of products. Owning every product completes the set: it adds completion_xp to the collector XP and
-- opens the reward (once per user); the bonus can only be claimed between bonus_start and bonus_end (unix).
CREATE TABLE IF NOT EXISTS `collectible_collections` (
  `collection_id` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL DEFAULT '',
  `completion_xp` int(11) NOT NULL DEFAULT 0,
  `reward_product_code` varchar(64) DEFAULT NULL,
  `bonus_product_code` varchar(64) DEFAULT NULL,
  `bonus_start` int(11) NOT NULL DEFAULT 0,
  `bonus_end` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The owned tokens. asset_id is the asset id the client sees; serial is the product's mint number.
CREATE TABLE IF NOT EXISTS `users_collectibles` (
  `asset_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `product_code` varchar(64) NOT NULL,
  `serial` int(11) NOT NULL DEFAULT 0,
  `source` varchar(16) NOT NULL DEFAULT 'admin',
  `created_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`asset_id`),
  KEY `idx_users_collectibles_user` (`user_id`),
  KEY `idx_users_collectibles_product` (`product_code`, `serial`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Claims made: collection rewards (reward:<id>), bonuses (bonus:<id>) and level rewards (level:<id>).
CREATE TABLE IF NOT EXISTS `users_collectible_claims` (
  `user_id` int(11) NOT NULL,
  `claim_key` varchar(96) NOT NULL,
  `claimed_amount` int(11) NOT NULL DEFAULT 0,
  `claimed_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `claim_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Per user: the highest collector XP ever reached and the relic stamps (mint tokens) owned.
CREATE TABLE IF NOT EXISTS `users_collector_stats` (
  `user_id` int(11) NOT NULL,
  `highest_score` int(11) NOT NULL DEFAULT 0,
  `mint_tokens` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The guild's rewards tab: a product every user of at least min_level can claim claim_limit times while
-- valid_from <= now <= valid_to (unix; 0 = no bound).
CREATE TABLE IF NOT EXISTS `collectible_level_rewards` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `min_level` int(11) NOT NULL DEFAULT 1,
  `product_code` varchar(64) NOT NULL,
  `claim_limit` int(11) NOT NULL DEFAULT 1,
  `valid_from` int(11) NOT NULL DEFAULT 0,
  `valid_to` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reward box furni (interaction collectible_reward_box): opening one removes it and mints one of these
-- products, picked by weight.
CREATE TABLE IF NOT EXISTS `collectible_reward_boxes` (
  `item_id` int(11) NOT NULL,
  `product_code` varchar(64) NOT NULL,
  `weight` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`item_id`, `product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Relics: a furni (items_base id) from the inventory that can be turned into a product for price stamps
-- between start_time and end_time (unix; 0 = no bound).
CREATE TABLE IF NOT EXISTS `collectible_mintables` (
  `item_id` int(11) NOT NULL,
  `product_code` varchar(64) NOT NULL,
  `price` int(11) NOT NULL DEFAULT 1,
  `start_time` int(11) NOT NULL DEFAULT 0,
  `end_time` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Stamp packs sold for silver in the relics tab.
CREATE TABLE IF NOT EXISTS `collectible_mint_token_offers` (
  `offer_id` int(11) NOT NULL AUTO_INCREMENT,
  `product_code` varchar(64) NOT NULL DEFAULT '',
  `silver_price` int(11) NOT NULL DEFAULT 0,
  `amount` int(11) NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`offer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.collectibles.enabled', '0', 'Collectibles (AIR Collectors Guild hub, inventory collectibles, reward boxes, relics): 1 = on, 0 = off. The client also needs collectibles.enabled in ui-config.'),
    ('hotel.collectibles.currency.emerald', '1001', 'Collectibles: users_currency type used for emeralds (shop currency).'),
    ('hotel.collectibles.currency.silver', '1000', 'Collectibles: users_currency type used for silver (transfer fee, relic stamps).'),
    ('hotel.collectibles.deliver_items', '1', 'Collectibles: also give the furni, badge or effect of a collectible when a user gets one (1 = on, 0 = token only).'),
    ('hotel.collectibles.level.xp_per_level', '1000', 'Collectibles: collector XP needed per collector level.'),
    ('hotel.collectibles.transfer.fee_silver', '10', 'Collectibles: silver paid per collectible when transferring all collectibles to another user.'),
    ('hotel.collectibles.minting.enabled', '1', 'Collectibles: relics (turning furni into collectibles with stamps) while collectibles are on.'),
    ('hotel.collectibles.emerald_credit.enabled', '1', 'Collectibles: nft_emerald_<amount> furni can be redeemed for emeralds while collectibles are on.')
ON DUPLICATE KEY UPDATE `value` = `value`;
