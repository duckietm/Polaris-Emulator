-- AIR's Collectibles marketplace (offer type 5): nft_* furni sold for emeralds next to the furni marketplace.
-- An offer posted through the collectibles flow is flagged here. The furni's name alone cannot tell the two apart:
-- an nft_* furni listed for credits before this feature keeps being a credit offer.
ALTER TABLE `marketplace_items` ADD COLUMN IF NOT EXISTS `nft` TINYINT(1) NOT NULL DEFAULT 0;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.marketplace.nft.enabled', '0', 'Collectibles marketplace: nft_* furni sold for emeralds (1 = on, 0 = off). Needs hotel.marketplace.enabled.'),
    ('hotel.marketplace.nft.price.min', '1', 'Collectibles marketplace: lowest asking price in emeralds.'),
    ('hotel.marketplace.nft.price.max', '100000', 'Collectibles marketplace: highest asking price in emeralds.'),
    ('hotel.marketplace.nft.offer.minutes', '10080', 'Collectibles marketplace: minutes an offer stays on sale before it expires.'),
    ('hotel.marketplace.nft.offers.max', '25', 'Collectibles marketplace: open offers one user may have at the same time.'),
    ('hotel.marketplace.nft.fee.percent', '5', 'Collectibles marketplace: share of the price (percent) kept as a fee; the seller gets the rest.'),
    ('hotel.marketplace.nft.halftax.limit', '0', 'Collectibles marketplace: price at which an extra fee of half the price applies, growing with the price (AIR halfTaxLimit). 0 = no extra fee.');
