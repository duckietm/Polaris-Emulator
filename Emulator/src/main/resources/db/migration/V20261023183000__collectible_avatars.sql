-- AIR avatar collectibles (the avatar editor's NFT tab: avatar/nft/NftAvatarsModel, HabboAvatarEditor): a
-- collectible product can be a whole avatar look that its owners wear. Ownership stays the token in
-- users_collectibles; see docs/collectibles.md. Additive and guarded: every statement is idempotent.

-- A product with a row here is wearable: its owners see it in the editor's NFT tab and can put it on.
-- figure is the look (as users.look), gender M or F, contract_key the AIR collection the editor colours and
-- names it by (habbo:avatar, habbo:clothes, habbo:avatar_genesis).
CREATE TABLE IF NOT EXISTS `collectible_avatar_outfits` (
  `product_code` varchar(64) NOT NULL,
  `figure` varchar(255) NOT NULL,
  `gender` char(1) NOT NULL DEFAULT 'M',
  `contract_key` varchar(32) NOT NULL DEFAULT 'habbo:avatar',
  PRIMARY KEY (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The avatar collectible a user last put on (users_collectibles.asset_id) and the look it replaced. It counts
-- as worn only while the user still owns the token and still has its look; any other look change takes it off.
CREATE TABLE IF NOT EXISTS `users_collectible_outfits` (
  `user_id` int(11) NOT NULL,
  `asset_id` bigint(20) NOT NULL,
  `fallback_figure` varchar(255) NOT NULL DEFAULT '',
  `fallback_gender` char(1) NOT NULL DEFAULT 'M',
  `worn_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.collectibles.avatars.enabled', '1', 'Collectibles: avatar collectibles (wearable looks in the avatar editor''s NFT tab) while collectibles are on (hotel.collectibles.enabled).')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_give_collectible', 'givecollectible;givenft'),
    ('commands.description.cmd_give_collectible', ':givecollectible <user> <product>'),
    ('commands.succes.cmd_give_collectible', '%user% got collectible %product% #%serial%.'),
    ('commands.error.cmd_give_collectible', 'Usage: :givecollectible <user> <product>'),
    ('commands.error.cmd_give_collectible.disabled', 'Collectibles are switched off (hotel.collectibles.enabled).'),
    ('commands.error.cmd_give_collectible.user', 'There is no user called %user%.'),
    ('commands.error.cmd_give_collectible.product', 'There is no collectible product %product%, or its mint limit is reached.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Permission definition for the command.
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_give_collectible', 1, 'Allows using :givecollectible to mint a collectible product (an avatar collectible too) to a user.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- Grant it to every staff rank (5 and above).
SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`id` >= 5;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` = ''cmd_give_collectible'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
