-- Marketplace selling tokens (AIR MarketplaceModel / buy_marketplace_tokens): every marketplace offer costs one
-- token. A user without tokens gets the can-make-offer answer 4 and the official buy-tokens window, which buys
-- `marketplace.tokens.amount` tokens for `marketplace.tokens.price` credits. Off by default, so hotels keep
-- selling for free until the owner turns it on.
CREATE TABLE IF NOT EXISTS `users_marketplace_tokens` (
  `user_id` int(11) NOT NULL,
  `tokens` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('marketplace.tokens.enabled', '0', 'Marketplace offers cost a selling token, bought in the buy-tokens window (1 = on, 0 = off, offers are free).'),
    ('marketplace.tokens.price', '1', 'Credits a batch of marketplace selling tokens costs.'),
    ('marketplace.tokens.amount', '5', 'Marketplace selling tokens in one batch (one token per offer).')
ON DUPLICATE KEY UPDATE `value` = `value`;
