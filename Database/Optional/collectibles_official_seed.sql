-- Collectibles (AIR Collectors Guild): Habbo's sets, shop offers, relics and stamp packs.
--
-- Fills the tables of V20261023171538__collectibles.sql (see docs/collectibles.md) with what Habbo's
-- live servers showed in October 2026:
--   * collectible_collections: the 73 sets of the Collections tab;
--   * collectible_products: the 511 products of those sets (collector XP, rarity, place in the set),
--     the 29 shop offers among them (emerald price), and the 74 relic products;
--   * collectible_mintables: the 74 relics (the furni a user turns into a collectible), their price in
--     Curator Stamps and their collect window;
--   * collectible_mint_token_offers: the 10 stamp packs, n stamps for 100 * n silver.
--
-- Where the values come from:
--   OBSERVED: the replies of Habbo's servers to the hub (shop offers, stamp packs, relics with their
--     windows, 2026-10-21), Habbo's public collectibles list (sets, set membership, collector XP,
--     rarity) and Habbo's collectibles.set.<id> texts (set names). They agree with the hub screenshots:
--     shop  nft_h26_witchchair 700 ("Witch's Chair"), nft_h26_witchworkstation 500,
--           nft_h26_spellingredients 150, nft_h26_ingredientshelf 300, nft_h24_showcase 50,
--           nft_h24_largeshowcase 75, nft_h23_paygate 300 and the Vintaque furni at 40 emeralds;
--     relic rare_dragonlamp*4 ("Black Dragon Lamp") 4 stamps, open until 2027-01-01 (72 days left on
--           the screenshot);
--     stamps 4 for 400 silver (one of the ten packs).
--   DEFAULT (not observed on Habbo, chosen here; change the switches below):
--     @set_completion_xp, the XP a complete set adds (Habbo's set boost is unknown): 0;
--     no set has a completion reward or a time-limited bonus, and no collector level rewards are added;
--     @relic_score / @relic_rarity for the 74 relic products: 10 XP, Common.
--   This hotel's conventions: a product code is Habbo's (the furni classname; a relic product is the
--     nft_ classname Habbo gives it), with '*' written as '.'; a furni product's item_type_id is its
--     items_base id. Clothing products (type 11) get their figure set ids from the furni's customparams:
--     items_base, else catalog_clothing, else the furnidata (Furniture/FurnitureData.json).
--   Not stored: Habbo marks the first three offers as featured; this protocol has no featured flag,
--     they lead the list. Relics are sent in items_base id order, not in Habbo's.
--
-- sort_order: a shop offer has its place in the shop (1-29), any other set product 100 + its place in
-- the set (so inside a set the offered furni come first), a relic product 500 + its place in the list.
--
-- Every furni is looked up by classname in items_base; a set product, offer or relic whose furni this
-- hotel does not have is skipped without an error, and a set is only added when one of its products is.
-- A relic mints its product's furni (nft_<classname>) when items_base has it, otherwise the furni itself.
--
-- Works whatever charset items_base uses (latin1 from old dumps or utf8mb4) and in strict SQL mode.
--
-- Safe to run more than once. OBSERVED values are (re)applied on every run; DEFAULT values only when a
-- row is created, so an operator's XP for a relic or a set stays. Products an operator put in the shop
-- stay there. Relic windows are kept once written, unless @relic_window_reset is 1 or the row came
-- from the first version of this seed.
--
-- The first version of this seed guessed. With @undo_earlier_guesses 1 (the default) a run also takes
-- its guesses back: the furni it put in the shop at the guessed 50 emeralds, the three wrongly matched
-- tiles (nft_sc_wanddisplay 50, nft_sc26_wanddisplay 75, nft_h26_broomstickrack 300), its relic_<x>
-- relics (a relic Habbo also offers becomes Habbo's, the others are disabled) and its 'curator_stamps'
-- pack. Tokens users already own are never touched.
--
-- Run it after the collectibles migration, e.g.
--   mysql -u <user> -p <database> < collectibles_official_seed.sql
-- then turn the feature on (hotel.collectibles.enabled = 1 in emulator_settings, collectibles.enabled
-- and nft.shop.enabled / nft.minting.enabled in the client ui-config). The tables are read on every
-- request, so no reload is needed.

SET @set_completion_xp := 0;       -- DEFAULT: XP a complete set adds
SET @relic_score := 10;            -- DEFAULT: collector XP of a relic product
SET @relic_rarity := 'Common';     -- DEFAULT: rarity of a relic product
SET @relic_window_days := 0;       -- 0: Habbo's windows (OBSERVED, they end between 2026-11-10 and
                                   -- 2027-01-01); N > 0: every relic opens now and ends in N days
SET @relic_window_reset := 0;      -- 1 rewrites the window of relics that already exist
SET @undo_earlier_guesses := 1;    -- 1 takes back the first version's guesses (see above)
SET @now := UNIX_TIMESTAMP();

-- items_base by classname, in one charset, so every lookup below can use an index.
DROP TEMPORARY TABLE IF EXISTS `tmp_collectibles_items`;
CREATE TEMPORARY TABLE `tmp_collectibles_items` (
    `id` INT UNSIGNED NOT NULL PRIMARY KEY,
    `item_name` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `type` VARCHAR(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `customparams` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    KEY `idx_item_name` (`item_name`)
);
INSERT INTO `tmp_collectibles_items` (`id`, `item_name`, `type`, `interaction_type`, `customparams`)
SELECT b.`id`,
       CONVERT(b.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci,
       CONVERT(b.`type` USING utf8mb4) COLLATE utf8mb4_general_ci,
       CONVERT(COALESCE(b.`interaction_type`, '') USING utf8mb4) COLLATE utf8mb4_general_ci,
       CONVERT(COALESCE(b.`customparams`, '') USING utf8mb4) COLLATE utf8mb4_general_ci
FROM `items_base` b;

DROP TEMPORARY TABLE IF EXISTS `tmp_col_sets`;
CREATE TEMPORARY TABLE `tmp_col_sets` (
    `collection_id` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL PRIMARY KEY,
    `name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `sort_order` INT NOT NULL
);

DROP TEMPORARY TABLE IF EXISTS `tmp_col_products`;
CREATE TEMPORARY TABLE `tmp_col_products` (
    `classname` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL PRIMARY KEY,
    `collection_id` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `score` INT NOT NULL,
    `rarity` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `set_position` INT NOT NULL,
    `emerald_price` INT NULL,        -- set from tmp_col_offers; NULL = not offered in the shop
    `shop_position` INT NULL,
    `item_id` INT UNSIGNED NULL,
    `item_type` VARCHAR(3) NULL,
    `is_clothing` TINYINT NOT NULL DEFAULT 0
);

DROP TEMPORARY TABLE IF EXISTS `tmp_col_offers`;
CREATE TEMPORARY TABLE `tmp_col_offers` (
    `classname` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL PRIMARY KEY,
    `emerald_price` INT NOT NULL,
    `featured` TINYINT NOT NULL,
    `shop_position` INT NOT NULL
);

DROP TEMPORARY TABLE IF EXISTS `tmp_col_relics`;
CREATE TEMPORARY TABLE `tmp_col_relics` (
    `base_classname` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `base_type` VARCHAR(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `product_classname` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `stamp_price` INT NOT NULL,
    `start_time` INT NOT NULL,
    `end_time` INT NOT NULL,
    `relic_position` INT NOT NULL PRIMARY KEY,
    `base_id` INT UNSIGNED NULL,
    `twin_id` INT UNSIGNED NULL,
    `twin_type` VARCHAR(3) NULL
);

DROP TEMPORARY TABLE IF EXISTS `tmp_col_stamp_packs`;
CREATE TEMPORARY TABLE `tmp_col_stamp_packs` (
    `offer_id` INT NOT NULL PRIMARY KEY,
    `product_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `silver_price` INT NOT NULL,
    `amount` INT NOT NULL
);

DROP TEMPORARY TABLE IF EXISTS `tmp_col_figure_sets`;
CREATE TEMPORARY TABLE `tmp_col_figure_sets` (
    `classname` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL PRIMARY KEY,
    `figure_set_ids` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL
);

-- ---------------------------------------------------------------- the data

-- Collections tab: the sets, in the order Habbo lists them (OBSERVED).
INSERT INTO `tmp_col_sets` (`collection_id`, `name`, `sort_order`) VALUES
    ('witch_scholar', 'Witch Scholar', 1), ('collectibles_5th_anniversary', 'Collectibles 5th Anniversary', 2),
    ('western_ranch', 'Western Ranch', 3), ('dragon_familiars', 'Dragon Familiars', 4),
    ('ivy_syndicate', 'Ivy Syndicate', 5), ('rainbow_party', 'Rainbow Party', 6),
    ('jungle_run', 'Jungle Run', 7), ('disco_fever', 'Disco Fever', 8),
    ('dungeon_master', 'Dungeon Master', 9), ('april_fools', 'April Fools', 10),
    ('film_buff', 'Film Buff', 11), ('slumber_hotel', 'Slumber Hotel', 12),
    ('lunar_new_year_2026', 'Lunar New Year 2026', 13), ('gamer_fanatic', 'Gamer Fanatic', 14),
    ('christmas_2025', 'Christmas 2025', 15), ('the_spellbound_set', 'The Spellbound Set', 16),
    ('habboween_2025', 'Habboween 2025', 17), ('4th_anniversary', 'Collectibles 4th Anniversary', 18),
    ('temple_of_olympus', 'Temple of Olympus', 19), ('habbo_25th_anniversary', 'Habbo 25th Anniversary', 20),
    ('summer_25', 'Summer 2025', 21), ('giftboxes_25', 'Gift Boxes 2025', 22),
    ('lost_monkey', 'Lost Monkey', 23), ('trippy_items_25', 'Trippy 2', 24),
    ('alice_in_habboland', 'Alice in Habboland', 25), ('fashion_month_2025', 'Fashion Month 2025', 26),
    ('tokyo_gacha', 'Tokyo Gacha', 27), ('sculpture_garden', 'Sculpture Garden', 28),
    ('lunar_new_year_2025', 'Lunar New Year 2025', 29), ('nye_2024', 'New Year 2024', 30),
    ('dream_kingdom', 'Dream Kingdom', 31), ('habboween_2024', 'Habboween 2024', 32),
    ('exploring_mexico', 'Exploring Mexico', 33), ('cirque_du_games', 'Cirque Du Games', 34),
    ('summer_2024_clothing', 'Summer 2024 Clothing', 35), ('summer_2024_furni', 'summer 2024 furni', 36),
    ('giftboxes_24', 'Gift Boxes 2024', 37), ('habbo_history_2024', 'Habbo History 2024', 38),
    ('rare_emeralds', 'Rare Emerald Furni', 39), ('graffiti_on_the_street', 'Graffiti on the Street', 40),
    ('maudes_mini_world', 'Maude''s Mini World', 41), ('dragonfly_farmstead', 'Dragonfly Farmstead', 42),
    ('last_ice_of_winter', 'The Last Ice of Winter', 43), ('Lunar_New_Year_2024', 'Lunar New Year 2024', 44),
    ('collectors_guild', 'Collectors Guild', 45), ('fansites_23', 'Fansites 2023', 46),
    ('rockstars_23', 'Rock Stars 2023', 47), ('habboween_23', 'Habboween 2023', 48),
    ('autumn_23', 'Autumn 2023', 49), ('animal_clothes_23', 'Animal Clothes 2023', 50),
    ('wall_street', 'Wall Street', 51), ('summer_23', 'Summer 2023', 52),
    ('giftboxes_23', 'Gift Boxes 2023', 53), ('action_figures', 'Action Figures', 54),
    ('ugc', 'UGC 2023', 55), ('habboX_23', 'Habbo X 2023', 56),
    ('vintaque', 'Vintaque', 57), ('spring_23', 'Spring 2023', 58),
    ('robots_23', 'Robots 2023', 59), ('trippy_items_23', 'Trippy 1', 60),
    ('forged', 'Forged', 61), ('extras_23', 'Extras 2023', 62),
    ('winter_23', 'Winter 2023', 63), ('xmas_22_clothes', 'Xmas 2022 Clothes', 64),
    ('habboween_22', 'Habboween 2022', 65), ('gift_boxes_22', 'Gift Boxes 2022', 66),
    ('summer_22', 'Summer 2022', 67), ('limited_editions_2022_2023', 'Limited Editions 2022-2023', 68),
    ('cpu_parts', 'CPU Parts', 69), ('ducks_22_23', 'Ducks 2022-2023', 70),
    ('keychar_collection', 'Keychairs', 71), ('extras_22', 'Extras 2022', 72),
    ('emerald_furni', 'Emerald Furni', 73);

-- Set products: furni classname, set, collector XP, rarity, place in the set (OBSERVED).
INSERT INTO `tmp_col_products` (`classname`, `collection_id`, `score`, `rarity`, `set_position`) VALUES
    ('clothing_nft26_nftwitchcape', 'witch_scholar', 100, 'Rare', 1), ('nft_h26_broomstickrack', 'witch_scholar', 15, 'Uncommon', 2),
    ('nft_h26_ingredientshelf', 'witch_scholar', 15, 'Uncommon', 3), ('nft_h26_spellingredients', 'witch_scholar', 10, 'Common', 4),
    ('nft_h26_witchworkstation', 'witch_scholar', 50, 'Rare', 5), ('nft_h26_witchchair', 'witch_scholar', 115, 'Epic', 6),
    ('nft_h26_annivfrosty', 'collectibles_5th_anniversary', 50, 'Rare', 1), ('nft_h26_trippypatch', 'collectibles_5th_anniversary', 115, 'Epic', 2),
    ('clothing_nftemeraldskin', 'collectibles_5th_anniversary', 500, 'Legendary', 3), ('clothing_nftstrawhat', 'western_ranch', 100, 'Rare', 1),
    ('clothing_nftstrawmouth', 'western_ranch', 30, 'Uncommon', 2), ('clothing_nftwesternshirt', 'western_ranch', 100, 'Rare', 3),
    ('clothing_nftwesternvest', 'western_ranch', 100, 'Rare', 4), ('nft_h26_brewingkit', 'western_ranch', 115, 'Epic', 5),
    ('nft_h26_farmtruck', 'western_ranch', 50, 'Rare', 6), ('clothing_nftshoulderdragon2', 'dragon_familiars', 2000, 'Legendary', 1),
    ('nft_h26_contraband', 'ivy_syndicate', 50, 'Rare', 1), ('nft_h26_crates', 'ivy_syndicate', 10, 'Common', 2),
    ('nft_h26_cardtable', 'ivy_syndicate', 15, 'Uncommon', 3), ('nft_h26_cardchair', 'ivy_syndicate', 15, 'Uncommon', 4),
    ('clothing_nftnewsboycap', 'ivy_syndicate', 100, 'Rare', 5), ('clothing_nftsuspenders', 'ivy_syndicate', 30, 'Uncommon', 6),
    ('clothing_nftmouthtape', 'ivy_syndicate', 30, 'Uncommon', 7), ('clothing_nftgoldgrills', 'ivy_syndicate', 100, 'Rare', 8),
    ('clothing_nftpartyshades', 'rainbow_party', 100, 'Rare', 1), ('clothing_nftrainbowpartyshades', 'rainbow_party', 30, 'Uncommon', 2),
    ('clothing_nftpalmbraid', 'rainbow_party', 30, 'Uncommon', 3), ('clothing_nftbeachboy', 'rainbow_party', 100, 'Rare', 4),
    ('clothing_nftrainbowswimshorts', 'rainbow_party', 30, 'Uncommon', 5), ('clothing_nftrainbowbikini', 'rainbow_party', 30, 'Uncommon', 6),
    ('nft_h26_pineapplefridge', 'rainbow_party', 150, 'Rare', 7), ('nft_h26_treasuremap2', 'jungle_run', 50, 'Rare', 1),
    ('nft_h26_sunlounger', 'jungle_run', 15, 'Uncommon', 2), ('nft_h26_bamboochair', 'jungle_run', 10, 'Common', 3),
    ('nft_h26_jungleplant1', 'jungle_run', 10, 'Common', 4), ('nft_h26_jungleplant2', 'jungle_run', 10, 'Common', 5),
    ('nft_h26_jungleplant3', 'jungle_run', 15, 'Uncommon', 6), ('nft_h26_losttoilet', 'jungle_run', 50, 'Rare', 7),
    ('nft_h26_haf_junglesantini', 'jungle_run', 115, 'Epic', 8), ('clothing_nftledhorns', 'disco_fever', 100, 'Rare', 1),
    ('clothing_nftledbracelet', 'disco_fever', 100, 'Rare', 2), ('clothing_nfttintedglasses', 'disco_fever', 100, 'Rare', 3),
    ('clothing_nftmusicalnotes', 'disco_fever', 30, 'Uncommon', 4), ('nft_h26_bigcd', 'disco_fever', 10, 'Common', 5),
    ('clothing_nftkeytar', 'disco_fever', 230, 'Epic', 6), ('clothing_nftyoyo', 'disco_fever', 100, 'Rare', 7),
    ('clothing_nftmayhair1', 'disco_fever', 100, 'Rare', 8), ('clothing_nftmayhair2', 'disco_fever', 100, 'Rare', 9),
    ('clothing_nftmayhair3', 'disco_fever', 100, 'Rare', 10), ('nft_h26_dngbattlerockingchair', 'dungeon_master', 50, 'Rare', 1),
    ('nft_h26_dngtable', 'dungeon_master', 10, 'Common', 2), ('nft_h26_dngstool', 'dungeon_master', 10, 'Common', 3),
    ('nft_h26_dngpillar', 'dungeon_master', 15, 'Uncommon', 4), ('nft_h26_dngbbq', 'dungeon_master', 50, 'Rare', 5),
    ('nft_h26_dngdragonsofa', 'dungeon_master', 50, 'Rare', 6), ('clothing_nftelvenface', 'dungeon_master', 100, 'Rare', 7),
    ('clothing_nftelvenface2', 'dungeon_master', 100, 'Rare', 8), ('nft_h26_aprilshrimp', 'april_fools', 250, 'Legendary', 1),
    ('nft_h26_poolfactionposter', 'film_buff', 15, 'Uncommon', 1), ('nft_h26_receptionistposter', 'film_buff', 15, 'Uncommon', 2),
    ('nft_h26_lotfposter', 'film_buff', 15, 'Uncommon', 3), ('nft_h26_figu1', 'film_buff', 15, 'Uncommon', 4),
    ('nft_h26_figu2', 'film_buff', 15, 'Uncommon', 5), ('nft_h26_figu3', 'film_buff', 15, 'Uncommon', 6),
    ('nft_h26_hafbox', 'film_buff', 50, 'Rare', 7), ('clothing_nftsoda', 'film_buff', 10, 'Common', 8),
    ('clothing_nftpopcorn', 'film_buff', 10, 'Common', 9), ('nft_h26_movienightchair', 'film_buff', 50, 'Rare', 10),
    ('nft_h26_bigtv', 'film_buff', 50, 'Rare', 11), ('nft_h26_sandmangnome', 'slumber_hotel', 100, 'Rare', 1),
    ('clothing_nftteddymask', 'slumber_hotel', 30, 'Uncommon', 2), ('clothing_nftpandamask', 'slumber_hotel', 30, 'Uncommon', 3),
    ('nft_h26_alarmboombox', 'slumber_hotel', 15, 'Uncommon', 4), ('nft_h26_discobed', 'slumber_hotel', 50, 'Rare', 5),
    ('nft_h26_bedtray', 'slumber_hotel', 15, 'Uncommon', 6), ('nft_h26_pillowssofa', 'slumber_hotel', 50, 'Rare', 7),
    ('clothing_nfthorseyearmark1', 'lunar_new_year_2026', 100, 'Rare', 1), ('clothing_nfthorseyearmark2', 'lunar_new_year_2026', 150, 'Rare', 2),
    ('clothing_nfthorseyearmark3', 'lunar_new_year_2026', 150, 'Rare', 3), ('clothing_nfthorseyearmark4', 'lunar_new_year_2026', 150, 'Rare', 4),
    ('nft_h26_drpgamingchair', 'gamer_fanatic', 50, 'Rare', 1), ('nft_h26_gamingsign', 'gamer_fanatic', 15, 'Uncommon', 2),
    ('nft_h26_joystick', 'gamer_fanatic', 15, 'Uncommon', 3), ('nft_h26_haf_holoboy', 'gamer_fanatic', 50, 'Rare', 4),
    ('nft_h26_haf_hologirl', 'gamer_fanatic', 50, 'Rare', 5), ('clothing_nfttubescarf', 'christmas_2025', 30, 'Uncommon', 1),
    ('clothing_nftwinte25jacket', 'christmas_2025', 230, 'Epic', 2), ('clothing_nftwinter25beanie', 'christmas_2025', 100, 'Rare', 3),
    ('clothing_nftwinter25pants', 'christmas_2025', 30, 'Uncommon', 4), ('clothing_nftwinter25boots', 'christmas_2025', 30, 'Uncommon', 5),
    ('clothing_nft2025santahat', 'christmas_2025', 30, 'Uncommon', 6), ('clothing_nftrudolphprint', 'christmas_2025', 20, 'Common', 7),
    ('clothing_nftxmasbonnieprint', 'christmas_2025', 20, 'Common', 8), ('clothing_nfthohohoprint', 'christmas_2025', 20, 'Common', 9),
    ('clothing_nftmerryxmasprint', 'christmas_2025', 20, 'Common', 10), ('clothing_nftrudolphsnose', 'christmas_2025', 100, 'Rare', 11),
    ('nft_h25_xmasgiftbox', 'christmas_2025', 15, 'Uncommon', 12), ('nft_h25_gingerbreadsofa', 'christmas_2025', 50, 'Rare', 13),
    ('nft_h25_gingerbreadhotel', 'christmas_2025', 15, 'Uncommon', 14), ('nft_h25_xmaslamp', 'christmas_2025', 50, 'Rare', 15),
    ('clothing_nftstarfacepaint', 'christmas_2025', 100, 'Rare', 16), ('clothing_nftheartfacepaint', 'christmas_2025', 100, 'Rare', 17),
    ('clothing_nftturkeyhat', 'christmas_2025', 100, 'Rare', 18), ('nft_h25_xmasicm', 'christmas_2025', 115, 'Epic', 19),
    ('clothing_nfthappy2026print', 'christmas_2025', 20, 'Common', 20), ('clothing_nft2026', 'christmas_2025', 30, 'Uncommon', 21),
    ('nft_h25_magiccauldron', 'the_spellbound_set', 10, 'Common', 1), ('clothing_nftswordnshiled', 'the_spellbound_set', 100, 'Rare', 2),
    ('nft_h25_goblin', 'the_spellbound_set', 50, 'Rare', 3), ('clothing_nftbighorns', 'the_spellbound_set', 100, 'Rare', 4),
    ('clothing_nftutility', 'the_spellbound_set', 100, 'Rare', 5), ('clothing_nftwizardshaircut', 'the_spellbound_set', 20, 'Common', 6),
    ('clothing_nftwizardsbeard', 'the_spellbound_set', 30, 'Uncommon', 7), ('clothing_nftwizardsglasses', 'the_spellbound_set', 20, 'Common', 8),
    ('clothing_nftwizardshat', 'the_spellbound_set', 100, 'Rare', 9), ('clothing_nftwizardsrobe', 'the_spellbound_set', 100, 'Rare', 10),
    ('clothing_nftfungalinfection', 'habboween_2025', 30, 'Uncommon', 1), ('clothing_nftflashlight', 'habboween_2025', 100, 'Rare', 2),
    ('nft_h25_gnomeofdoom', 'habboween_2025', 15, 'Uncommon', 3), ('nft_h25_pumpkinpod', 'habboween_2025', 15, 'Uncommon', 4),
    ('nft_h25_skelebutler', 'habboween_2025', 50, 'Rare', 5), ('clothing_nftombrecut1', '4th_anniversary', 30, 'Uncommon', 1),
    ('clothing_nftombrecut2', '4th_anniversary', 30, 'Uncommon', 2), ('clothing_nfthobbyhorse1', '4th_anniversary', 100, 'Rare', 3),
    ('clothing_nfthobbyhorse2', '4th_anniversary', 100, 'Rare', 4), ('clothing_nfthobbyhorse3', '4th_anniversary', 100, 'Rare', 5),
    ('nft_h25_armasbarrelchair', '4th_anniversary', 10, 'Common', 6), ('nft_h25_armasrockingchair', '4th_anniversary', 10, 'Common', 7),
    ('nft_h25_zeusdrinks', 'temple_of_olympus', 50, 'Rare', 1), ('nft_h25_pegasusstatue', 'temple_of_olympus', 50, 'Rare', 2),
    ('nft_h25_zeuspod', 'temple_of_olympus', 15, 'Uncommon', 3), ('clothing_nftfrogfloat', 'habbo_25th_anniversary', 30, 'Uncommon', 1),
    ('clothing_nftseahorsefloat', 'habbo_25th_anniversary', 30, 'Uncommon', 2), ('clothing_nft25balloonblue', 'habbo_25th_anniversary', 30, 'Uncommon', 3),
    ('clothing_nft25balloonred', 'habbo_25th_anniversary', 30, 'Uncommon', 4), ('nft_h25_robocroc', 'habbo_25th_anniversary', 50, 'Rare', 5),
    ('clothing_nftcola', 'habbo_25th_anniversary', 100, 'Rare', 6), ('clothing_nft25earrings3', 'habbo_25th_anniversary', 100, 'Rare', 7),
    ('nft_h25_haf_squabble', 'habbo_25th_anniversary', 115, 'Epic', 8), ('clothing_nftpotpalm', 'summer_25', 100, 'Rare', 1),
    ('clothing_nftbubblebath', 'summer_25', 100, 'Rare', 2), ('nft_h25_coolbox1', 'giftboxes_25', 10, 'Common', 1),
    ('nft_h25_coolbox2', 'giftboxes_25', 15, 'Uncommon', 2), ('nft_h25_monkeybzn_chair', 'lost_monkey', 50, 'Rare', 1),
    ('nft_h25_monkeybzn_drinks', 'lost_monkey', 50, 'Rare', 2), ('nft_h25_monkeybzn_gang', 'lost_monkey', 15, 'Uncommon', 3),
    ('clothing_nftbackmonkey', 'lost_monkey', 100, 'Rare', 4), ('nft_h25_monkeybzn_pond', 'lost_monkey', 50, 'Rare', 5),
    ('nft_h25_trippy_scifirocket', 'trippy_items_25', 333, 'Epic', 1), ('nft_h25_trippy_pillar', 'trippy_items_25', 50, 'Rare', 2),
    ('nft_h25_trippy_screen', 'trippy_items_25', 50, 'Rare', 3), ('nft_h25_trippy_sleepingbag', 'trippy_items_25', 50, 'Rare', 4),
    ('nft_h23_trippy_turntable', 'trippy_items_25', 300, 'Epic', 5), ('nft_h25_trippy_scifiport', 'trippy_items_25', 50, 'Rare', 6),
    ('nft_h25_trippy_marquee', 'trippy_items_25', 50, 'Rare', 7), ('nft_h25_trippy_scifidoor', 'trippy_items_25', 50, 'Rare', 8),
    ('nft_h23_trippy_samovar', 'trippy_items_25', 50, 'Rare', 9), ('nft_h25_alicefigure', 'alice_in_habboland', 50, 'Rare', 1),
    ('nft_h25_cheshirecatfigure', 'alice_in_habboland', 50, 'Rare', 2), ('clothing_nftcheshiregrin', 'alice_in_habboland', 100, 'Rare', 3),
    ('nft_h25_caterpillarfigure', 'alice_in_habboland', 50, 'Rare', 4), ('nft_h25_alhambrahookah', 'alice_in_habboland', 50, 'Rare', 5),
    ('nft_h25_alhambrapillowpile', 'alice_in_habboland', 15, 'Uncommon', 6), ('nft_h25_hatterfigure', 'alice_in_habboland', 50, 'Rare', 7),
    ('clothing_nft10shillings6p', 'alice_in_habboland', 30, 'Uncommon', 8), ('clothing_nftmop', 'fashion_month_2025', 100, 'Rare', 1),
    ('clothing_nfttrench', 'fashion_month_2025', 100, 'Rare', 2), ('clothing_nftbunnyshades1', 'fashion_month_2025', 30, 'Uncommon', 3),
    ('clothing_nftbunnyshades2', 'fashion_month_2025', 30, 'Uncommon', 4), ('nft_h25_deluxemannequin', 'fashion_month_2025', 50, 'Rare', 5),
    ('clothing_nfttallcrown', 'fashion_month_2025', 230, 'Epic', 6), ('clothing_nftblingnecklace', 'fashion_month_2025', 100, 'Rare', 7),
    ('nft_h25_haf_reddevil', 'tokyo_gacha', 50, 'Rare', 1), ('clothing_nftscarface', 'tokyo_gacha', 100, 'Rare', 2),
    ('clothing_nftbigsword', 'tokyo_gacha', 100, 'Rare', 3), ('clothing_nftakaiakuma', 'tokyo_gacha', 100, 'Rare', 4),
    ('clothing_nftyumekawa', 'tokyo_gacha', 100, 'Rare', 5), ('clothing_nftcatheadband', 'tokyo_gacha', 30, 'Uncommon', 6),
    ('clothing_nftcatmaiddress', 'tokyo_gacha', 100, 'Rare', 7), ('clothing_nftcatslippers', 'tokyo_gacha', 30, 'Uncommon', 8),
    ('clothing_nftcatheadband2', 'tokyo_gacha', 30, 'Uncommon', 9), ('clothing_nftcatmaiddress2', 'tokyo_gacha', 100, 'Rare', 10),
    ('clothing_nftcatslippers2', 'tokyo_gacha', 30, 'Uncommon', 11), ('clothing_nftwinter', 'sculpture_garden', 100, 'Rare', 1),
    ('clothing_nftwinter1', 'sculpture_garden', 100, 'Rare', 2), ('clothing_nftwinter2', 'sculpture_garden', 100, 'Rare', 3),
    ('clothing_nftwinter3', 'sculpture_garden', 100, 'Rare', 4), ('nft_h25_flowers1', 'sculpture_garden', 10, 'Common', 5),
    ('nft_h25_flowers2', 'sculpture_garden', 10, 'Common', 6), ('nft_h25_flowers3', 'sculpture_garden', 10, 'Common', 7),
    ('nft_h25_flowers4', 'sculpture_garden', 10, 'Common', 8), ('nft_h25_flowers5', 'sculpture_garden', 10, 'Common', 9),
    ('nft_h25_flowers6', 'sculpture_garden', 10, 'Common', 10), ('nft_h25_flowers7', 'sculpture_garden', 10, 'Common', 11),
    ('nft_h25_flowers8', 'sculpture_garden', 10, 'Common', 12), ('nft_h25_flowers9', 'sculpture_garden', 10, 'Common', 13),
    ('nft_h25_flowers10', 'sculpture_garden', 10, 'Common', 14), ('nft_h25_owlsculpture', 'sculpture_garden', 50, 'Rare', 15),
    ('clothing_nftbluehearteyes', 'sculpture_garden', 100, 'Rare', 16), ('clothing_nftpinkhearteyes', 'sculpture_garden', 100, 'Rare', 17),
    ('clothing_nftsnakeyearmark1', 'lunar_new_year_2025', 100, 'Rare', 1), ('clothing_nft2025', 'nye_2024', 30, 'Uncommon', 1),
    ('nft_u24_autumnpatch', 'dream_kingdom', 15, 'Uncommon', 1), ('nft_h24_cloudswing', 'dream_kingdom', 10, 'Common', 2),
    ('nft_h24_layoncloud', 'dream_kingdom', 10, 'Common', 3), ('nft_h24_sitoncloud', 'dream_kingdom', 10, 'Common', 4),
    ('clothing_nftspdemon', 'dream_kingdom', 230, 'Epic', 5), ('nft_h24_bday_napmaster', 'dream_kingdom', 50, 'Rare', 6),
    ('nft_h24_hween_nobox', 'habboween_2024', 10, 'Common', 1), ('nft_h24_spookyhotel', 'habboween_2024', 10, 'Common', 2),
    ('clothing_nftbloody', 'habboween_2024', 100, 'Rare', 3), ('nft_h24_catacombduck', 'habboween_2024', 15, 'Uncommon', 4),
    ('nft_h24_haf_zombie', 'habboween_2024', 50, 'Rare', 5), ('nft_h24_skull1', 'exploring_mexico', 15, 'Uncommon', 1),
    ('nft_h24_skull2', 'exploring_mexico', 15, 'Uncommon', 2), ('nft_h24_skull3', 'exploring_mexico', 15, 'Uncommon', 3),
    ('clothing_nftnacho', 'exploring_mexico', 30, 'Uncommon', 4), ('clothing_nftchili1', 'exploring_mexico', 30, 'Uncommon', 5),
    ('clothing_nftchili2', 'exploring_mexico', 30, 'Uncommon', 6), ('clothing_nftchili3', 'exploring_mexico', 30, 'Uncommon', 7),
    ('clothing_nftcurlybun', 'exploring_mexico', 30, 'Uncommon', 8), ('clothing_nftbroccolicut', 'exploring_mexico', 30, 'Uncommon', 9),
    ('nft_h24_haf_habbolympian', 'cirque_du_games', 50, 'Rare', 1), ('nft_h24_circuscolortile', 'cirque_du_games', 10, 'Common', 2),
    ('nft_h24_circusarrowtile', 'cirque_du_games', 10, 'Common', 3), ('nft_h24_circusspiketile', 'cirque_du_games', 10, 'Common', 4),
    ('nft_h24_circusletters', 'cirque_du_games', 10, 'Common', 5), ('nft_h24_circusswitch', 'cirque_du_games', 10, 'Common', 6),
    ('nft_h24_circusmeter', 'cirque_du_games', 10, 'Common', 7), ('nft_h24_circusarrowbutton', 'cirque_du_games', 10, 'Common', 8),
    ('nft_h24_circusbutton', 'cirque_du_games', 10, 'Common', 9), ('nft_h24_clownstandin1', 'cirque_du_games', 15, 'Uncommon', 10),
    ('nft_h24_clownstandin2', 'cirque_du_games', 15, 'Uncommon', 11), ('clothing_nftsummercap1', 'summer_2024_clothing', 20, 'Common', 1),
    ('clothing_nftsummercap2', 'summer_2024_clothing', 20, 'Common', 2), ('clothing_nftsummercap3', 'summer_2024_clothing', 20, 'Common', 3),
    ('clothing_nftsummercap4', 'summer_2024_clothing', 20, 'Common', 4), ('clothing_nftsummercap5', 'summer_2024_clothing', 30, 'Uncommon', 5),
    ('clothing_nftsummercap6', 'summer_2024_clothing', 30, 'Uncommon', 6), ('nft_h24_cefalu_wall', 'summer_2024_furni', 10, 'Common', 1),
    ('nft_h24_cefalu_floor', 'summer_2024_furni', 10, 'Common', 2), ('nft_h24_luggage1', 'giftboxes_24', 10, 'Common', 1),
    ('nft_h24_luggage2', 'giftboxes_24', 10, 'Common', 2), ('nft_h24_luggage3', 'giftboxes_24', 15, 'Uncommon', 3),
    ('nft_h24_bday_box1', 'giftboxes_24', 10, 'Common', 4), ('nft_h24_bday_box2', 'giftboxes_24', 15, 'Uncommon', 5),
    ('nft_h24_xmas_present1', 'giftboxes_24', 10, 'Common', 6), ('nft_h24_xmas_present2', 'giftboxes_24', 15, 'Uncommon', 7),
    ('nft_h24_rastabeanbag', 'habbo_history_2024', 10, 'Common', 1), ('nft_h24_bbchair', 'habbo_history_2024', 10, 'Common', 2),
    ('clothing_nftconsole', 'habbo_history_2024', 30, 'Uncommon', 3), ('clothing_nftpurse', 'habbo_history_2024', 30, 'Uncommon', 4),
    ('nft_h24_lidoduck', 'habbo_history_2024', 10, 'Common', 5), ('nft_h24_habburgers', 'habbo_history_2024', 15, 'Uncommon', 6),
    ('nft_h24_hugetube', 'habbo_history_2024', 50, 'Rare', 7), ('nft_h24_podsofa1', 'habbo_history_2024', 15, 'Uncommon', 8),
    ('nft_h24_podsofa2', 'habbo_history_2024', 15, 'Uncommon', 9), ('nft_h24_podsofa3', 'habbo_history_2024', 15, 'Uncommon', 10),
    ('nft_h24_podsofa4', 'habbo_history_2024', 15, 'Uncommon', 11), ('nft_emerald_eggmerald', 'rare_emeralds', 50, 'Rare', 1),
    ('nft_emerald_emerhand', 'rare_emeralds', 115, 'Epic', 2), ('nft_emerald_pumpkin_2000', 'rare_emeralds', 50, 'Rare', 3),
    ('nft_emerald_xmastree_2000', 'rare_emeralds', 50, 'Rare', 4), ('nft_emerald_heart_2000', 'rare_emeralds', 50, 'Rare', 5),
    ('nft_emerald_tome_5000', 'rare_emeralds', 115, 'Epic', 6), ('clothing_nftgraffititanktop', 'graffiti_on_the_street', 30, 'Uncommon', 1),
    ('clothing_nftgraffiticroptop', 'graffiti_on_the_street', 30, 'Uncommon', 2), ('clothing_nftgraffitipants', 'graffiti_on_the_street', 30, 'Uncommon', 3),
    ('clothing_nftstreetboy', 'graffiti_on_the_street', 30, 'Uncommon', 4), ('clothing_nftstreetgirl', 'graffiti_on_the_street', 30, 'Uncommon', 5),
    ('nft_h24_graffiti1', 'graffiti_on_the_street', 15, 'Uncommon', 6), ('nft_h24_graffiti2', 'graffiti_on_the_street', 15, 'Uncommon', 7),
    ('nft_h24_graffiti3', 'graffiti_on_the_street', 15, 'Uncommon', 8), ('nft_h24_haf_noobette', 'maudes_mini_world', 10, 'Common', 1),
    ('nft_h24_pyramid', 'maudes_mini_world', 10, 'Common', 2), ('nft_h24_moai', 'maudes_mini_world', 10, 'Common', 3),
    ('nft_h24_eiffel', 'maudes_mini_world', 10, 'Common', 4), ('nft_h24_statueofliberty', 'maudes_mini_world', 10, 'Common', 5),
    ('nft_h24_thunderfruit', 'dragonfly_farmstead', 10, 'Common', 1), ('nft_h24_shinythunderfruit', 'dragonfly_farmstead', 15, 'Uncommon', 2),
    ('clothing_nftraspberrycap', 'dragonfly_farmstead', 30, 'Uncommon', 3), ('clothing_nftraspberryshirt', 'dragonfly_farmstead', 30, 'Uncommon', 4),
    ('clothing_nftraspberrybag', 'dragonfly_farmstead', 100, 'Rare', 5), ('clothing_nftraspberryshoes', 'dragonfly_farmstead', 20, 'Common', 6),
    ('clothing_nftblueberrycap', 'dragonfly_farmstead', 30, 'Uncommon', 7), ('clothing_nftblueberryshirt', 'dragonfly_farmstead', 30, 'Uncommon', 8),
    ('clothing_nftblueberrybag', 'dragonfly_farmstead', 100, 'Rare', 9), ('clothing_nftblueberryshoes', 'dragonfly_farmstead', 20, 'Common', 10),
    ('clothing_nftstrawberrycap', 'dragonfly_farmstead', 30, 'Uncommon', 11), ('clothing_nftstrawberryshirt', 'dragonfly_farmstead', 30, 'Uncommon', 12),
    ('clothing_nftstrawberrybag', 'dragonfly_farmstead', 100, 'Rare', 13), ('clothing_nftstrawberryshoes', 'dragonfly_farmstead', 20, 'Common', 14),
    ('nft_h24_haf_frosty', 'last_ice_of_winter', 15, 'Uncommon', 1), ('clothing_nfthearteyes', 'last_ice_of_winter', 230, 'Epic', 2),
    ('clothing_nftwhitebeanie', 'last_ice_of_winter', 20, 'Common', 3), ('clothing_nftpinkbeanie', 'last_ice_of_winter', 20, 'Common', 4),
    ('clothing_nftblackbeanie', 'last_ice_of_winter', 20, 'Common', 5), ('clothing_nftwintergreen', 'last_ice_of_winter', 20, 'Common', 6),
    ('clothing_nftwinterpink', 'last_ice_of_winter', 20, 'Common', 7), ('clothing_nftwinterblack', 'last_ice_of_winter', 20, 'Common', 8),
    ('clothing_nftsleepless', 'last_ice_of_winter', 100, 'Rare', 9), ('clothing_nftdragonyearmark1', 'Lunar_New_Year_2024', 30, 'Uncommon', 1),
    ('nft_h24_globe_cabinet', 'collectors_guild', 50, 'Rare', 1), ('nft_h24_showcase', 'collectors_guild', 10, 'Common', 2),
    ('nft_h24_largeshowcase', 'collectors_guild', 10, 'Common', 3), ('chatstyle_clay', 'collectors_guild', 173, 'Rare', 4),
    ('nft_h25_collsmallchest', 'collectors_guild', 10, 'Common', 5), ('nft_h25_collbigchest', 'collectors_guild', 15, 'Uncommon', 6),
    ('nft_h25_collsofa', 'collectors_guild', 15, 'Uncommon', 7), ('nft_h25_collbench', 'collectors_guild', 15, 'Uncommon', 8),
    ('nft_h25_colltable', 'collectors_guild', 15, 'Uncommon', 9), ('nft_h25_collpillar', 'collectors_guild', 50, 'Rare', 10),
    ('nft_h23_partner_habbo3', 'fansites_23', 10, 'Common', 1), ('HCX15', 'fansites_23', 10, 'Common', 2),
    ('clothing_nft23rock1hr', 'rockstars_23', 100, 'Rare', 1), ('clothing_nftbobbajacket', 'rockstars_23', 100, 'Rare', 2),
    ('clothing_nftskullsnroses1', 'rockstars_23', 30, 'Uncommon', 3), ('clothing_nftskullsnroses2', 'rockstars_23', 30, 'Uncommon', 4),
    ('clothing_nft23rock2hr', 'rockstars_23', 100, 'Rare', 5), ('clothing_nftrippedjeans', 'rockstars_23', 30, 'Uncommon', 6),
    ('clothing_nftrippedshorts', 'rockstars_23', 30, 'Uncommon', 7), ('clothing_nft23rock3hr', 'rockstars_23', 100, 'Rare', 8),
    ('clothing_nft23rock1fa', 'rockstars_23', 100, 'Rare', 9), ('clothing_nftpiercings', 'rockstars_23', 30, 'Uncommon', 10),
    ('clothing_nft23rock2fa', 'rockstars_23', 30, 'Uncommon', 11), ('clothing_nft23rock3fa', 'rockstars_23', 30, 'Uncommon', 12),
    ('clothing_nft23rock4fa', 'rockstars_23', 30, 'Uncommon', 13), ('nft_h23_haf_mummy', 'habboween_23', 15, 'Uncommon', 1),
    ('nft_h23_sobekstatue', 'habboween_23', 10, 'Common', 2), ('nft_h23_bastetgoddess', 'habboween_23', 10, 'Common', 3),
    ('clothing_nftyellowraincoat', 'autumn_23', 30, 'Uncommon', 1), ('clothing_nftblueraincoat', 'autumn_23', 30, 'Uncommon', 2),
    ('clothing_nftpinkraincoat', 'autumn_23', 30, 'Uncommon', 3), ('clothing_nftyellowrainboots', 'autumn_23', 30, 'Uncommon', 4),
    ('clothing_nftbluerainboots', 'autumn_23', 30, 'Uncommon', 5), ('clothing_nftpinkrainboots', 'autumn_23', 30, 'Uncommon', 6),
    ('clothing_nftcurlers', 'autumn_23', 100, 'Rare', 7), ('clothing_nftshortdreads', 'autumn_23', 100, 'Rare', 8),
    ('clothing_nftbrighteyes', 'autumn_23', 100, 'Rare', 9), ('clothing_nftvivideyes', 'autumn_23', 100, 'Rare', 10),
    ('clothing_nftfroghoodie', 'animal_clothes_23', 30, 'Uncommon', 1), ('clothing_nftdinohoodie', 'animal_clothes_23', 30, 'Uncommon', 2),
    ('clothing_nftunicornhoodie', 'animal_clothes_23', 30, 'Uncommon', 3), ('clothing_nftfrogslippers', 'animal_clothes_23', 30, 'Uncommon', 4),
    ('clothing_nftdinoslippers', 'animal_clothes_23', 30, 'Uncommon', 5), ('clothing_nftunicornslippers', 'animal_clothes_23', 30, 'Uncommon', 6),
    ('nft_h23_bronzebull', 'wall_street', 15, 'Uncommon', 1), ('nft_h23_bronzebear', 'wall_street', 15, 'Uncommon', 2),
    ('nft_h23_dollarrug', 'wall_street', 50, 'Rare', 3), ('clothing_nftblueswimshorts', 'summer_23', 30, 'Uncommon', 1),
    ('clothing_nftpinkswimshorts', 'summer_23', 30, 'Uncommon', 2), ('clothing_nftbluebikini', 'summer_23', 30, 'Uncommon', 3),
    ('clothing_nftpinkbikini', 'summer_23', 30, 'Uncommon', 4), ('nft_h23_coolbox', 'giftboxes_23', 10, 'Common', 1),
    ('nft_h23_premiumcoolbox', 'giftboxes_23', 15, 'Uncommon', 2), ('nft_h23_bday_gift1', 'giftboxes_23', 10, 'Common', 3),
    ('nft_h23_bday_gift2', 'giftboxes_23', 10, 'Common', 4), ('nft_h23_xmas_present1', 'giftboxes_23', 10, 'Common', 5),
    ('nft_h23_xmas_present2', 'giftboxes_23', 50, 'Rare', 6), ('nft_h23_haf_noob', 'action_figures', 50, 'Common', 1),
    ('nft_h23_sonft_poster1', 'ugc', 10, 'Common', 1), ('nft_h23_sonft_poster2', 'ugc', 10, 'Common', 2),
    ('nft_h23_sonft_poster3', 'ugc', 10, 'Common', 3), ('nft_h23_sonft_poster4', 'ugc', 10, 'Common', 4),
    ('nft_u23_xmaseasel', 'ugc', 115, 'Epic', 5), ('nft_h23_teleport', 'habboX_23', 300, 'Uncommon', 1),
    ('nft_h23_paygate', 'habboX_23', 300, 'Uncommon', 2), ('nft_h23_vintaque_sofa', 'vintaque', 10, 'Common', 1),
    ('nft_h23_vintaque_sofa_r', 'vintaque', 10, 'Common', 2), ('nft_h23_vintaque_sofa_p', 'vintaque', 10, 'Common', 3),
    ('nft_h23_vintaque_table', 'vintaque', 10, 'Common', 4), ('nft_h23_vintaque_stool', 'vintaque', 10, 'Common', 5),
    ('nft_h23_vintaque_stool_r', 'vintaque', 10, 'Common', 6), ('nft_h23_vintaque_stool_p', 'vintaque', 10, 'Common', 7),
    ('nft_h23_vintaque_chair', 'vintaque', 10, 'Common', 8), ('nft_h23_vintaque_chair_r', 'vintaque', 10, 'Common', 9),
    ('nft_h23_vintaque_chair_p', 'vintaque', 10, 'Common', 10), ('nft_h23_vintaque_desk', 'vintaque', 10, 'Common', 11),
    ('nft_h23_vintaque_fence', 'vintaque', 10, 'Common', 12), ('nft_h23_vintaque_fence_r', 'vintaque', 10, 'Common', 13),
    ('nft_h23_vintaque_fence_p', 'vintaque', 10, 'Common', 14), ('nft_h23_vintaque_nightstand', 'vintaque', 10, 'Common', 15),
    ('clothing_nftspringbrimhat', 'spring_23', 30, 'Uncommon', 1), ('clothing_nftsunflowershirt', 'spring_23', 30, 'Uncommon', 2),
    ('clothing_nftsunflowershorts', 'spring_23', 30, 'Uncommon', 3), ('clothing_nftsunflowerdress', 'spring_23', 30, 'Uncommon', 4),
    ('nft_h23_redgardener', 'robots_23', 10, 'Common', 1), ('nft_h23_bluegardener', 'robots_23', 10, 'Common', 2),
    ('nft_h23_yellowgardener', 'robots_23', 10, 'Common', 3), ('nft_h23_catcafebot', 'robots_23', 50, 'Rare', 4),
    ('nft_h23_boxrobot', 'robots_23', 15, 'Uncommon', 5), ('nft_h23_redtank', 'robots_23', 50, 'Rare', 6),
    ('nft_h23_drone', 'robots_23', 15, 'Uncommon', 7), ('nft_h23_flyingscooter', 'robots_23', 50, 'Rare', 8),
    ('nft_h23_trippy_elephant', 'trippy_items_23', 115, 'Epic', 1), ('nft_h23_trippy_dinoegg', 'trippy_items_23', 115, 'Epic', 2),
    ('nft_h23_trippy_rarestand', 'trippy_items_23', 115, 'Epic', 3), ('nft_h23_trippy_hemingway', 'trippy_items_23', 115, 'Epic', 4),
    ('nft_h23_trippy_fan', 'trippy_items_23', 115, 'Epic', 5), ('nft_h23_mechaflooring', 'forged', 10, 'Common', 1),
    ('nft_h23_mecharoller', 'forged', 10, 'Common', 2), ('nft_h23_mechabench', 'forged', 10, 'Common', 3),
    ('nft_h23_mechachair', 'forged', 10, 'Common', 4), ('nft_h23_mechajuniper', 'forged', 10, 'Common', 5),
    ('clothing_nftmechapack', 'forged', 30, 'Uncommon', 6), ('nft_h23_mechabar', 'forged', 10, 'Common', 7),
    ('nft_h23_mechabarcorner', 'forged', 10, 'Common', 8), ('nft_h23_mechagate', 'forged', 10, 'Common', 9),
    ('nft_h23_mechagolem', 'forged', 50, 'Rare', 10), ('nft_h23_mechastove', 'forged', 10, 'Common', 11),
    ('nft_h23_mechawall', 'forged', 10, 'Common', 12), ('clothing_nftmechahelmet', 'forged', 30, 'Uncommon', 13),
    ('clothing_nftduckpack', 'extras_23', 100, 'Rare', 1), ('nft_h23_sakuratemple', 'extras_23', 50, 'Rare', 2),
    ('nft_h23_vintaque_statue', 'extras_23', 50, 'Rare', 3), ('nft_h23_duckmascot', 'extras_23', 50, 'Rare', 4),
    ('nft_h23_scarecrow', 'extras_23', 50, 'Rare', 5), ('nft_h23_starfox', 'extras_23', 10, 'Common', 6),
    ('clothing_nft2024', 'extras_23', 20, 'Common', 7), ('nft_h23_retrococoa', 'winter_23', 50, 'Rare', 1),
    ('clothing_nftwintercoatbrown', 'winter_23', 30, 'Uncommon', 2), ('clothing_nftwintercoatwhite', 'winter_23', 30, 'Uncommon', 3),
    ('clothing_nftwintercoatcheck', 'winter_23', 30, 'Uncommon', 4), ('clothing_nftberetbrown', 'winter_23', 30, 'Uncommon', 5),
    ('clothing_nftberetwhite', 'winter_23', 30, 'Uncommon', 6), ('clothing_nftberetcheck', 'winter_23', 30, 'Uncommon', 7),
    ('clothing_nftwintercapbrown', 'winter_23', 30, 'Uncommon', 8), ('clothing_nftwintercapwhite', 'winter_23', 30, 'Uncommon', 9),
    ('clothing_nftwintercapcheck', 'winter_23', 30, 'Uncommon', 10), ('nft_h23_valentinessofa', 'winter_23', 50, 'Rare', 11),
    ('clothing_nftwinterbreeze', 'winter_23', 100, 'Rare', 12), ('clothing_nftbookworm', 'winter_23', 30, 'Uncommon', 13),
    ('clothing_nftwinterweaves', 'winter_23', 100, 'Rare', 14), ('clothing_nftrastasanta', 'xmas_22_clothes', 100, 'Rare', 1),
    ('clothing_nftxmasdress', 'xmas_22_clothes', 30, 'Uncommon', 2), ('clothing_nftxmaspants', 'xmas_22_clothes', 30, 'Uncommon', 3),
    ('clothing_nftxmasshoes', 'xmas_22_clothes', 20, 'Common', 4), ('clothing_nft2023tuxedo', 'xmas_22_clothes', 100, 'Rare', 5),
    ('clothing_nftdoll', 'habboween_22', 30, 'Uncommon', 1), ('clothing_nftdolldress', 'habboween_22', 30, 'Uncommon', 2),
    ('clothing_nftdollshoes', 'habboween_22', 20, 'Common', 3), ('clothing_nftdollface', 'habboween_22', 100, 'Rare', 4),
    ('clothing_nftdollhair', 'habboween_22', 100, 'Rare', 5), ('clothing_nftdollhair2', 'habboween_22', 100, 'Rare', 6),
    ('nft_h22_bday_gift1', 'gift_boxes_22', 10, 'Common', 1), ('nft_h22_bday_gift2', 'gift_boxes_22', 10, 'Common', 2),
    ('nft_h22_xmas22present1', 'gift_boxes_22', 10, 'Common', 3), ('nft_h22_xmas22present2', 'gift_boxes_22', 50, 'Rare', 4),
    ('clothing_nfttropical', 'summer_22', 100, 'Rare', 1), ('clothing_nfttropicalcrop', 'summer_22', 100, 'Rare', 2),
    ('clothing_nftbluestarshades', 'summer_22', 20, 'Common', 3), ('clothing_nftblueheartshades', 'summer_22', 20, 'Common', 4),
    ('clothing_nftpinkstarshades', 'summer_22', 20, 'Common', 5), ('clothing_nftpinkheartshades', 'summer_22', 20, 'Common', 6),
    ('nft_h22_chiptable_gold', 'limited_editions_2022_2023', 930, 'Epic', 1), ('nft_h22_dragonskeleton', 'limited_editions_2022_2023', 1156, 'Legendary', 2),
    ('nft_h22_trippyfountain', 'limited_editions_2022_2023', 4000, 'Legendary', 3), ('nft_h23_bigkey', 'limited_editions_2022_2023', 2000, 'Legendary', 4),
    ('nft_h23_roomomatic', 'limited_editions_2022_2023', 600, 'Epic', 5), ('nft_u23_sonft_sofa', 'limited_editions_2022_2023', 600, 'Rare', 6),
    ('clothing_nftstatue', 'limited_editions_2022_2023', 1000, 'Legendary', 7), ('nft_h22_chiptable', 'cpu_parts', 15, 'Uncommon', 1),
    ('nft_h22_fusetronics_rug', 'cpu_parts', 10, 'Common', 2), ('nft_h22_fusetronics_ledr', 'cpu_parts', 10, 'Common', 3),
    ('nft_h22_fusetronics_ledg', 'cpu_parts', 10, 'Common', 4), ('nft_h22_fusetronics_ledy', 'cpu_parts', 10, 'Common', 5),
    ('nft_h22_fusetronics_res', 'cpu_parts', 10, 'Common', 6), ('nft_h22_fusetronics_coil', 'cpu_parts', 15, 'Uncommon', 7),
    ('nft_h22_fusetronics_fan', 'cpu_parts', 15, 'Uncommon', 8), ('nft_h22_fusetronics_caps', 'cpu_parts', 15, 'Uncommon', 9),
    ('nft_h22_gangnamduckclouds', 'ducks_22_23', 15, 'Uncommon', 1), ('nft_h22_gangnamduckstars', 'ducks_22_23', 15, 'Uncommon', 2),
    ('nft_h22_gangnamduckgold', 'ducks_22_23', 15, 'Uncommon', 3), ('nft_h22_gangnamduckhearts', 'ducks_22_23', 15, 'Uncommon', 4),
    ('nft_h23_invisible_duck', 'ducks_22_23', 15, 'Uncommon', 5), ('nft_h22_keychair_t', 'keychar_collection', 10, 'Common', 1),
    ('nft_h22_keychair_f', 'keychar_collection', 10, 'Common', 2), ('nft_h22_keychair_n', 'keychar_collection', 10, 'Common', 3),
    ('nft_h22_keysofa_shift', 'keychar_collection', 10, 'Common', 4), ('nft_h22_keychair_a', 'keychar_collection', 10, 'Common', 5),
    ('nft_h22_keychair_x', 'keychar_collection', 10, 'Common', 6), ('nft_h22_keychair_o', 'keychar_collection', 10, 'Common', 7),
    ('nft_h22_keychair_q', 'keychar_collection', 10, 'Common', 8), ('nft_h22_keychair_c', 'keychar_collection', 10, 'Common', 9),
    ('nft_h22_keychair_h', 'keychar_collection', 10, 'Common', 10), ('nft_h22_keychair_i', 'keychar_collection', 10, 'Common', 11),
    ('nft_h22_keychair_p', 'keychar_collection', 10, 'Common', 12), ('nft_h22_keychair_r', 'keychar_collection', 10, 'Common', 13),
    ('nft_h22_keychair_u', 'keychar_collection', 10, 'Common', 14), ('nft_h22_keychair_g', 'keychar_collection', 10, 'Common', 15),
    ('nft_h22_keychair_l', 'keychar_collection', 10, 'Common', 16), ('nft_h22_keychair_e', 'keychar_collection', 10, 'Common', 17),
    ('nft_h22_keychair_d', 'keychar_collection', 10, 'Common', 18), ('nft_h22_keychair_s', 'keychar_collection', 10, 'Common', 19),
    ('nft_h22_keychair_v', 'keychar_collection', 10, 'Common', 20), ('nft_h22_keychair_w', 'keychar_collection', 10, 'Common', 21),
    ('nft_h22_keychair_y', 'keychar_collection', 10, 'Common', 22), ('nft_h22_keychair_z', 'keychar_collection', 10, 'Common', 23),
    ('nft_h22_keychair_j', 'keychar_collection', 10, 'Common', 24), ('nft_h22_keychair_k', 'keychar_collection', 10, 'Common', 25),
    ('nft_h22_keychair_b', 'keychar_collection', 10, 'Common', 26), ('nft_h22_keychair_m', 'keychar_collection', 10, 'Common', 27),
    ('nft_h22_dragonlamp', 'extras_22', 115, 'Epic', 1), ('nft_h22_lightbulb', 'extras_22', 10, 'Common', 2),
    ('nft_h22_frosty', 'extras_22', 50, 'Rare', 3), ('nft_h22_sharkaquarium', 'extras_22', 115, 'Epic', 4),
    ('nft_emerald_10', 'emerald_furni', 10, 'Common', 1), ('nft_emerald_50', 'emerald_furni', 10, 'Common', 2),
    ('nft_emerald_100', 'emerald_furni', 10, 'Common', 3), ('nft_emerald_500', 'emerald_furni', 10, 'Common', 4),
    ('nft_emerald_1000', 'emerald_furni', 15, 'Uncommon', 5);

-- Shop tab, in Habbo's order: classname, emerald price, featured (OBSERVED; every offer is unlimited).
INSERT INTO `tmp_col_offers` (`classname`, `emerald_price`, `featured`, `shop_position`) VALUES
    ('nft_emerald_tome_5000', 5000, 1, 1), ('nft_h26_witchchair', 700, 1, 2),
    ('nft_h26_witchworkstation', 500, 1, 3), ('nft_h26_spellingredients', 150, 0, 4),
    ('nft_h26_ingredientshelf', 300, 0, 5), ('nft_h24_showcase', 50, 0, 6),
    ('nft_h24_largeshowcase', 75, 0, 7), ('nft_h23_paygate', 300, 0, 8),
    ('nft_h23_vintaque_nightstand', 40, 0, 9), ('nft_h23_vintaque_chair_r', 40, 0, 10),
    ('nft_h23_vintaque_fence_p', 40, 0, 11), ('nft_h23_vintaque_chair_p', 40, 0, 12),
    ('nft_h23_teleport', 300, 0, 13), ('nft_h23_vintaque_fence', 40, 0, 14),
    ('nft_h23_vintaque_desk', 40, 0, 15), ('nft_h23_vintaque_fence_r', 40, 0, 16),
    ('nft_h23_vintaque_chair', 40, 0, 17), ('nft_h23_vintaque_table', 40, 0, 18),
    ('nft_h23_vintaque_sofa_r', 40, 0, 19), ('nft_h23_vintaque_sofa_p', 40, 0, 20),
    ('nft_h23_vintaque_sofa', 40, 0, 21), ('nft_h23_vintaque_stool_p', 40, 0, 22),
    ('nft_h23_vintaque_stool', 40, 0, 23), ('nft_h23_vintaque_stool_r', 40, 0, 24),
    ('nft_emerald_100', 101, 0, 25), ('nft_emerald_1000', 1001, 0, 26),
    ('nft_emerald_500', 501, 0, 27), ('nft_emerald_50', 51, 0, 28),
    ('nft_emerald_10', 11, 0, 29);

-- Relics tab: the furni (classname, s floor / i wall), the product it becomes, the price in stamps and the
-- collect window in unix seconds (UTC), in Habbo's order (OBSERVED).
INSERT INTO `tmp_col_relics` (`base_classname`, `base_type`, `product_classname`, `stamp_price`, `start_time`, `end_time`, `relic_position`) VALUES
    ('sc26_flyingbook', 's', 'nft_sc26_flyingbook', 1, 1792148400, 1794391140, 1),  -- 2026-10-16 11:00 to 2026-11-11 09:59 UTC
    ('sc26_alchemykit', 's', 'nft_sc26_alchemykit', 1, 1792148400, 1794391140, 2),  -- 2026-10-16 11:00 to 2026-11-11 09:59 UTC
    ('sc26_transmogrification', 's', 'nft_sc_transmogrification', 1, 1792148400, 1794391140, 3),  -- 2026-10-16 11:00 to 2026-11-11 09:59 UTC
    ('sc26_wanddisplay', 's', 'nft_sc_wanddisplay', 1, 1792148400, 1794391140, 4),  -- 2026-10-16 11:00 to 2026-11-11 09:59 UTC
    ('rare_dragonlamp*4', 's', 'nft_rare_dragonlamp*4', 4, 1790834400, 1798797600, 5),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('duck_zombie', 's', 'nft_duck_zombie', 1, 1790834400, 1798797600, 6),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween_c17_mimic', 's', 'nft_hween_c17_mimic', 1, 1790834400, 1798797600, 7),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween13_throne', 's', 'nft_hween13_throne', 1, 1790834400, 1798797600, 8),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween15_horseman5', 's', 'nft_hween15_horseman5', 1, 1790834400, 1798797600, 9),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween15_saintini', 's', 'nft_hween15_saintini', 1, 1790834400, 1798797600, 10),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween15_saintta', 's', 'nft_hween15_saintta', 1, 1790834400, 1798797600, 11),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween15_evilraider', 's', 'nft_hween15_evilraider', 1, 1790834400, 1798797600, 12),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween15_evilfrank', 's', 'nft_hween15_evilfrank', 1, 1790834400, 1798797600, 13),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween12_moon', 's', 'nft_hween12_moon', 3, 1790834400, 1798797600, 14),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween_c16_cabinet2', 's', 'nft_hween_c16_cabinet2', 1, 1790834400, 1798797600, 15),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween_c16_cabinet', 's', 'nft_hween_c16_cabinet', 1, 1790834400, 1798797600, 16),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween11_fountain', 's', 'nft_hween11_fountain', 1, 1790834400, 1798797600, 17),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('diamond_painting18', 'i', 'nft_diamond_painting18', 1, 1790834400, 1798797600, 18),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween_r19_weepingwillow', 's', 'nft_hween_r19_weepingwillow', 1, 1790834400, 1798797600, 19),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween_r19_witchfamiliar', 's', 'nft_hween_r19_witchfamiliar', 1, 1790834400, 1798797600, 20),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('gothicfountain', 'i', 'nft_gothicfountain', 1, 1790834400, 1798797600, 21),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('gothicfountain2', 'i', 'nft_gothicfountain2', 1, 1790834400, 1798797600, 22),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween09_organ', 's', 'nft_hween09_organ', 1, 1790834400, 1798797600, 23),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('cannon', 's', 'nft_cannon', 1, 1790834400, 1798797600, 24),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll1', 's', 'nft_hween14_doll1', 1, 1790834400, 1798797600, 25),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll2', 's', 'nft_hween14_doll2', 1, 1790834400, 1798797600, 26),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll3', 's', 'nft_hween14_doll3', 1, 1790834400, 1798797600, 27),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll4', 's', 'nft_hween14_doll4', 1, 1790834400, 1798797600, 28),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll5', 's', 'nft_hween14_doll5', 1, 1790834400, 1798797600, 29),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll6', 's', 'nft_hween14_doll6', 1, 1790834400, 1798797600, 30),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll7', 's', 'nft_hween14_doll7', 1, 1790834400, 1798797600, 31),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll8', 's', 'nft_hween14_doll8', 1, 1790834400, 1798797600, 32),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll9', 's', 'nft_hween14_doll9', 1, 1790834400, 1798797600, 33),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('hween14_doll10', 's', 'nft_hween14_doll10', 1, 1790834400, 1798797600, 34),  -- 2026-10-01 06:00 to 2027-01-01 10:00 UTC
    ('wooden_screen*0', 's', 'nft_wooden_screen*0', 1, 1788328800, 1796464800, 35),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('rare_blackrosegold_scifiport', 's', 'nft_rare_blackrosegold_scifiport', 1, 1788328800, 1796464800, 36),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('plushie_r20_hammock', 's', 'nft_plushie_r20_hammock', 1, 1788328800, 1796464800, 37),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('plushie_r20_picnictree', 's', 'nft_plushie_r20_picnictree', 1, 1788328800, 1796464800, 38),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('fest_r19_gemowl', 's', 'nft_fest_r19_gemowl', 1, 1788328800, 1796464800, 39),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('suncity_r19_glassfountain', 's', 'nft_suncity_r19_glassfountain', 1, 1788328800, 1796464800, 40),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('rainyday_r19_rainbear', 's', 'nft_rainyday_r19_rainbear', 1, 1788328800, 1796464800, 41),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('rainyday_r19_fluffbun', 's', 'nft_rainyday_r19_fluffbun', 1, 1788328800, 1796464800, 42),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('js_r16_heli', 's', 'nft_js_r16_heli', 1, 1788328800, 1796464800, 43),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('js_r16_shark', 's', 'nft_js_r16_shark', 1, 1788328800, 1796464800, 44),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('attic15_telescope', 's', 'nft_attic15_telescope', 1, 1788328800, 1796464800, 45),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('carnival15_hotair_brazil', 's', 'nft_carnival15_hotair_brazil', 1, 1788328800, 1796464800, 46),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('school_gate', 's', 'nft_school_gate', 1, 1788328800, 1796464800, 47),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('bonusrare16_3*1', 's', 'nft_bonusrare16_3*1', 1, 1788328800, 1796464800, 48),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('bonusrare16_1*2', 's', 'nft_bonusrare16_1*2', 1, 1788328800, 1796464800, 49),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('bonusrare16_2*1', 's', 'nft_bonusrare16_2*1', 1, 1788328800, 1796464800, 50),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('bonusrare16_3*2', 's', 'nft_bonusrare16_3*2', 1, 1788328800, 1796464800, 51),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('rare_beehive_bulb*3', 's', 'nft_rare_beehive_bulb*3', 1, 1788328800, 1796464800, 52),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('rare_r20_balloonchair', 's', 'nft_rare_r20_balloonchair', 1, 1788328800, 1796464800, 53),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('rare_r20_wardrobe', 's', 'nft_rare_r20_wardrobe', 1, 1788328800, 1796464800, 54),  -- 2026-09-02 06:00 to 2026-12-05 10:00 UTC
    ('matic_cont_alien', 's', 'nft_matic_cont_alien', 1, 1785996000, 1794304800, 55),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('matic_cont_frank', 's', 'nft_matic_cont_frank', 1, 1785996000, 1794304800, 56),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('matic_cont_duck', 's', 'nft_matic_cont_duck', 1, 1785996000, 1794304800, 57),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('matic_mech', 's', 'nft_matic_mech', 1, 1785996000, 1794304800, 58),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('rare_fan*10', 's', 'nft_rare_fan*10', 1, 1785996000, 1794304800, 59),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('rare_colourable_elephant_statue*2', 's', 'nft_rare_colourable_elephant_statue*2', 1, 1785996000, 1794304800, 60),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('eco_table2', 's', 'nft_eco_table2', 1, 1785996000, 1794304800, 61),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('eco_table3', 's', 'nft_eco_table3', 1, 1785996000, 1794304800, 62),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('eco_light1', 's', 'nft_eco_light1', 1, 1785996000, 1794304800, 63),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('eco_light2', 's', 'nft_eco_light2', 1, 1785996000, 1794304800, 64),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('rare_colourable_dragonlamp*3', 's', 'nft_rare_colourable_dragonlamp*3', 5, 1785996000, 1794304800, 65),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('diamond_painting39', 's', 'nft_diamond_painting39', 1, 1785996000, 1794304800, 66),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('diamond_painting50', 's', 'nft_diamond_painting50', 1, 1785996000, 1794304800, 67),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('diamond_painting47', 's', 'nft_diamond_painting47', 1, 1785996000, 1794304800, 68),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('diamond_painting42', 'i', 'nft_diamond_painting42', 1, 1785996000, 1794304800, 69),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('diamond_painting45', 'i', 'nft_diamond_painting45', 1, 1785996000, 1794304800, 70),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('paris15_poodle', 's', 'nft_paris15_poodle', 1, 1785996000, 1794304800, 71),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('bonusrare16_1*1', 's', 'nft_bonusrare16_1*1', 1, 1785996000, 1794304800, 72),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('bonusrare16_2*2', 's', 'nft_bonusrare16_2*2', 1, 1785996000, 1794304800, 73),  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC
    ('rare_r24_htuberchair', 's', 'nft_rare_r24_htuberchair', 1, 1785996000, 1794304800, 74);  -- 2026-08-06 06:00 to 2026-11-10 10:00 UTC

-- Stamp packs: offer id, product code, silver price, stamps (OBSERVED).
INSERT INTO `tmp_col_stamp_packs` (`offer_id`, `product_code`, `silver_price`, `amount`) VALUES
    (29308, 'nft_mint_token_purchase', 100, 1), (29309, 'nft_mint_token_purchase_2', 200, 2),
    (29310, 'nft_mint_token_purchase_3', 300, 3), (29311, 'nft_mint_token_purchase_4', 400, 4),
    (29312, 'nft_mint_token_purchase_5', 500, 5), (29313, 'nft_mint_token_purchase_6', 600, 6),
    (29314, 'nft_mint_token_purchase_7', 700, 7), (29315, 'nft_mint_token_purchase_8', 800, 8),
    (29316, 'nft_mint_token_purchase_9', 900, 9), (29317, 'nft_mint_token_purchase_10', 1000, 10);

-- Clothing furni (the clothing products above and every other clothing_nft furni): the figure set ids in
-- their furnidata customparams (Furniture/FurnitureData.json).
INSERT INTO `tmp_col_figure_sets` (`classname`, `figure_set_ids`) VALUES
    ('clothing_nft10shillings6p', '6096'), ('clothing_nft2023tuxedo', '5104,5105,5106'),
    ('clothing_nft2024', '5462'), ('clothing_nft2025', '5946'),
    ('clothing_nft2025santahat', '6283'), ('clothing_nft2026', '6288'),
    ('clothing_nft23partyhat1', '5344'), ('clothing_nft23partyhat2', '5345'),
    ('clothing_nft23rock1fa', '5384'), ('clothing_nft23rock1hr', '5388'),
    ('clothing_nft23rock2fa', '5385'), ('clothing_nft23rock2hr', '5389'),
    ('clothing_nft23rock3fa', '5386'), ('clothing_nft23rock3hr', '5390'),
    ('clothing_nft23rock4fa', '5387'), ('clothing_nft25balloonblue', '6191'),
    ('clothing_nft25balloonred', '6192'), ('clothing_nft25earrings3', '6194'),
    ('clothing_nft26_bananahelmet', '6609'), ('clothing_nft26_birdnest', '6608'),
    ('clothing_nft26_camopaint', '6546,6610'), ('clothing_nft26_cottoncandy', '6595'),
    ('clothing_nft26_cutlery', '6593'), ('clothing_nft26_determinedface', '6591,6596'),
    ('clothing_nft26_haircomb', '6624'), ('clothing_nft26_hopefulface', '6592,6597'),
    ('clothing_nft26_nftwitchcape', '6581,6582'), ('clothing_nft26_nightgoggles', '6599'),
    ('clothing_nft26_tacticalvest', '6602'), ('clothing_nft26_trashcan', '6621,6622'),
    ('clothing_nft26_zigzagmustache', '6623'), ('clothing_nftabcd', '5932'),
    ('clothing_nftafk', '5884'), ('clothing_nftakaiakuma', '6034'),
    ('clothing_nftanime', '5874,5875'), ('clothing_nftantagonist', '5890,6009'),
    ('clothing_nftartistset', '5947,5948,5949,5950'), ('clothing_nftbabychickbackpack', '5463'),
    ('clothing_nftbabyrudolf', '5842'), ('clothing_nftbabysnowman', '5843'),
    ('clothing_nftbackmonkey', '6149'), ('clothing_nftballoonbuddy', '6618'),
    ('clothing_nftballooncrown1', '5342'), ('clothing_nftballooncrown2', '5343'),
    ('clothing_nftbaubles1', '5893'), ('clothing_nftbaubles2', '5894'),
    ('clothing_nftbdayribbon', '5339'), ('clothing_nftbeachboy', '6496'),
    ('clothing_nftbeebackpack', '5575'), ('clothing_nftbeetails', '5555'),
    ('clothing_nftberetbrown', '5129'), ('clothing_nftberetcheck', '5131'),
    ('clothing_nftberetwhite', '5130'), ('clothing_nftberryshades', '5612,5613,5614'),
    ('clothing_nftbighorns', '6254'), ('clothing_nftbiglollipop', '5829'),
    ('clothing_nftbigsword', '6044'), ('clothing_nftbiohazardjacket', '6233'),
    ('clothing_nftbionic', '6200'), ('clothing_nftblackbeanie', '5512'),
    ('clothing_nftblingnecklace', '6075'), ('clothing_nftbloody', '5798,5799'),
    ('clothing_nftbloodycrown', '5704'), ('clothing_nftblueberrybag', '5539'),
    ('clothing_nftblueberrycap', '5536'), ('clothing_nftblueberryshirt', '5527,5530,5547'),
    ('clothing_nftblueberryshoes', '5542'), ('clothing_nftbluebikini', '5259,5260,5261'),
    ('clothing_nftbluefloat', '5227'), ('clothing_nftbluehearteyes', '6026,6027'),
    ('clothing_nftblueheartshades', '4986'), ('clothing_nftbluerainboots', '5329'),
    ('clothing_nftblueraincoat', '5323,5326'), ('clothing_nftbluerose', '6369'),
    ('clothing_nftbluestarshades', '4985'), ('clothing_nftblueswimshorts', '5265'),
    ('clothing_nftbobbajacket', '5400'), ('clothing_nftbonniecateyeshades', '6053'),
    ('clothing_nftbonniehitcheddress', '6061,6062'), ('clothing_nftbonnielonglonghair', '6063'),
    ('clothing_nftbonnieshoestights', '6064'), ('clothing_nftbonusshades', '5743'),
    ('clothing_nftbookworm', '5135'), ('clothing_nftbowandquiver', '6213,6214'),
    ('clothing_nftbrat', '5882'), ('clothing_nftbrighteyes', '5316,5317'),
    ('clothing_nftbroccolicut', '5756'), ('clothing_nftbrokenglasses', '6196'),
    ('clothing_nftbronzejunglemask', '6483'), ('clothing_nftbronzeskin', '6001,6002'),
    ('clothing_nftbubblebath', '6147,6148'), ('clothing_nftbunnycapeoutfit', '5944,5945'),
    ('clothing_nftbunnyshades1', '6073'), ('clothing_nftbunnyshades2', '6074'),
    ('clothing_nftbunnyshoes', '5928'), ('clothing_nftcakehat', '5335'),
    ('clothing_nftcakeoutfit', '6230'), ('clothing_nftcandle', '5337'),
    ('clothing_nftcandycane', '5431'), ('clothing_nftcartoonhero', '5888,6007'),
    ('clothing_nftcartoonheroine', '5889,6008'), ('clothing_nftcat', '6069,6070'),
    ('clothing_nftcatheadband', '6036'), ('clothing_nftcatheadband2', '6037'),
    ('clothing_nftcatmaiddress', '6038,6040'), ('clothing_nftcatmaiddress2', '6039,6041'),
    ('clothing_nftcatslippers', '6042'), ('clothing_nftcatslippers2', '6043'),
    ('clothing_nftcfbeanie', '5929'), ('clothing_nftcfbeaniecol', '5987'),
    ('clothing_nftcheshiregrin', '6095'), ('clothing_nftchili1', '5752'),
    ('clothing_nftchili2', '5753'), ('clothing_nftchili3', '5754'),
    ('clothing_nftchiton', '5713,5714'), ('clothing_nftchocoicecream', '6132'),
    ('clothing_nftchocolatepin', '5436'), ('clothing_nftcocoaprint', '5432'),
    ('clothing_nftcola', '6199'), ('clothing_nftcollectiblescap', '5417'),
    ('clothing_nftcollectiblesprint', '6614'), ('clothing_nftcolorbandana', '5867'),
    ('clothing_nftcolorshirt', '5858,5859'), ('clothing_nftcolorshirtribbon', '5862,5863'),
    ('clothing_nftcolorshirtwatch', '5860,5861'), ('clothing_nftcolorsneakers', '5869'),
    ('clothing_nftcolorsneakers2', '5871'), ('clothing_nftcolorwidebrim', '5865'),
    ('clothing_nftconsole', '5615'), ('clothing_nftcurlers', '5320'),
    ('clothing_nftcurlybun', '5755'), ('clothing_nftdenimcap', '6133'),
    ('clothing_nftdenimcroptop', '6135,6136'), ('clothing_nftdenimjacket', '6134'),
    ('clothing_nftdenimshorts', '6137'), ('clothing_nftdinohoodie', '5288,5289,5293'),
    ('clothing_nftdinoslippers', '5296'), ('clothing_nftdoll', '5036,5037'),
    ('clothing_nftdolldress', '5038'), ('clothing_nftdollface', '5041'),
    ('clothing_nftdollhair', '5039'), ('clothing_nftdollhair2', '5040'),
    ('clothing_nftdollshoes', '5032'), ('clothing_nftdonutfloat', '5251'),
    ('clothing_nftdragoncrown', '5532'), ('clothing_nftdragonwings', '5771'),
    ('clothing_nftdragonyearmark1', '5494'), ('clothing_nftdragonyearmark2', '5495'),
    ('clothing_nftdragonyearmark3', '5496'), ('clothing_nftdragonyearmark4', '5497'),
    ('clothing_nftduckfloat', '5250'), ('clothing_nftduckpack', '5136'),
    ('clothing_nftdummy', '5740,5741'), ('clothing_nfteastereggcrown', '5915'),
    ('clothing_nftelvenface', '6438,6440'), ('clothing_nftelvenface2', '6439,6441'),
    ('clothing_nftemeraldskin', '6616,6617'), ('clothing_nfteyebow', '5758'),
    ('clothing_nftfestashirt', '6117'), ('clothing_nftflamingofloat', '5252'),
    ('clothing_nftflashlight', '6234'), ('clothing_nftfloraradiance', '5523'),
    ('clothing_nftflowerearrings', '6489'), ('clothing_nftflowerfairy', '6028,6033'),
    ('clothing_nftflowershades1', '6138'), ('clothing_nftflowershades2', '6139'),
    ('clothing_nftflowershades3', '6140'), ('clothing_nftflowershades4', '6141'),
    ('clothing_nftflowershades5', '6142'), ('clothing_nftflyingheart', '6370'),
    ('clothing_nftfourtail', '5438,5439'), ('clothing_nftframeless1', '5641'),
    ('clothing_nftframeless2', '5642'), ('clothing_nftfreckles', '5837,5838'),
    ('clothing_nftfreckles2', '5839,5840'), ('clothing_nftfrogfloat', '6189'),
    ('clothing_nftfroghoodie', '5286,5287,5292'), ('clothing_nftfrogslippers', '5295'),
    ('clothing_nftfrostybucket', '5336'), ('clothing_nftfrozenface', '5760'),
    ('clothing_nftfrozenscarf', '5759'), ('clothing_nftfungalinfection', '6231'),
    ('clothing_nftgameover', '5880'), ('clothing_nftgardenshoes1', '5639'),
    ('clothing_nftgardenshoes2', '5640'), ('clothing_nftgiantbunnytail', '5502'),
    ('clothing_nftgiftbox', '5498,5499'), ('clothing_nftgiftbox2', '5500,5501'),
    ('clothing_nftgoldgrills', '6534'), ('clothing_nftgoldjunglemask', '6485'),
    ('clothing_nftgoldskin', '6005,6006'), ('clothing_nftgoodvsevil', '5813,5827,5828'),
    ('clothing_nftgraffiticroptop', '5587,5588'), ('clothing_nftgraffitipants', '5584'),
    ('clothing_nftgraffititanktop', '5585,5586'), ('clothing_nftgreek1', '5711'),
    ('clothing_nftgreek2', '5712'), ('clothing_nfthabbo25crown', '6195'),
    ('clothing_nfthabbo25necklace3', '6193'), ('clothing_nfthabbocap', '4949'),
    ('clothing_nfthabbocap23', '5450'), ('clothing_nfthabbohoodie', '4952,4953'),
    ('clothing_nfthabbohoodie23', '5446,5447'), ('clothing_nfthabboshirt', '4950,4951'),
    ('clothing_nfthabboshirt23', '5448,5449'), ('clothing_nfthaha', '5873'),
    ('clothing_nfthappy2026print', '6287'), ('clothing_nftheartearrings', '6368'),
    ('clothing_nfthearteyes', '5490'), ('clothing_nftheartfacepaint', '6286'),
    ('clothing_nftheterocromia', '6329,6330,6331,6332,6333,6334,6335,6336,6337,6338,6339,6340'), ('clothing_nfthiphop', '5878,5879'),
    ('clothing_nfthobbyhorse1', '6227'), ('clothing_nfthobbyhorse2', '6228'),
    ('clothing_nfthobbyhorse3', '6229'), ('clothing_nfthohohoprint', '6281'),
    ('clothing_nftholidaycut', '5437'), ('clothing_nfthollywoodsmile', '6396'),
    ('clothing_nfthoneyjar', '5583'), ('clothing_nfthorsecrown', '6366'),
    ('clothing_nfthorseyearmark1', '6362'), ('clothing_nfthorseyearmark2', '6363'),
    ('clothing_nfthorseyearmark3', '6364'), ('clothing_nfthorseyearmark4', '6365'),
    ('clothing_nfticaruscrown', '6356'), ('clothing_nfticecreambag1', '5253'),
    ('clothing_nfticecreambag2', '5254'), ('clothing_nfticecreambag3', '5255'),
    ('clothing_nftjestermask', '5697'), ('clothing_nftjuicebox1', '5256'),
    ('clothing_nftjuicebox2', '5257'), ('clothing_nftjuicebox3', '5258'),
    ('clothing_nftkeytar', '6468'), ('clothing_nftking', '5885'),
    ('clothing_nftkissmark', '6367'), ('clothing_nftkpop', '5876,5877'),
    ('clothing_nftleatherwidebrim', '5864'), ('clothing_nftledbracelet', '6445,6446'),
    ('clothing_nftledhorns', '6443,6444'), ('clothing_nftlittlemonkey', '6487'),
    ('clothing_nftlollipop', '5433'), ('clothing_nftlollipop2', '5434'),
    ('clothing_nftlongflowyhair', '5503'), ('clothing_nftmarshmallowbag', '5550'),
    ('clothing_nftmarshmallowhair', '5566'), ('clothing_nftmarshmallowsocks', '5551'),
    ('clothing_nftmayhair1', '6465'), ('clothing_nftmayhair2', '6466'),
    ('clothing_nftmayhair3', '6467'), ('clothing_nftmduckhat', '6413'),
    ('clothing_nftmechahelmet', '5194'), ('clothing_nftmechapack', '5193'),
    ('clothing_nftmerryxmasprint', '6282'), ('clothing_nftmessycut', '5841'),
    ('clothing_nftmhandshirt', '6414,6415'), ('clothing_nftminibonnie', '5775,5776'),
    ('clothing_nftminidonnie', '5780,5781'), ('clothing_nftminidonnie2', '5790'),
    ('clothing_nftminiduck', '5676,5678'), ('clothing_nftminifrank', '5674,5675'),
    ('clothing_nftminifrank2', '5788'), ('clothing_nftminihank', '5786,5787'),
    ('clothing_nftminimaude', '5778,5779'), ('clothing_nftminimaude2', '5789'),
    ('clothing_nftminimuumio', '5784,5785'), ('clothing_nftminimyrtle', '5782,5783'),
    ('clothing_nftmininoob', '5719,5673'), ('clothing_nftmltdshirt', '6418,6419'),
    ('clothing_nftmonkeyfriend', '6150'), ('clothing_nftmoon', '6373'),
    ('clothing_nftmop', '6078'), ('clothing_nftmouthtape', '6469'),
    ('clothing_nftmrareshirt', '6416,6417'), ('clothing_nftmusicalnotes', '6448'),
    ('clothing_nftmythichair', '5887'), ('clothing_nftnacho', '5751'),
    ('clothing_nftneondevilmask', '5703'), ('clothing_nftnewsboycap', '6471'),
    ('clothing_nftnoob', '5881'), ('clothing_nftnpc', '5883'),
    ('clothing_nftombrecut1', '6220'), ('clothing_nftombrecut2', '6221'),
    ('clothing_nftorigamihat1', '5340'), ('clothing_nftorigamihat2', '5341'),
    ('clothing_nftpalmbraid', '6495'), ('clothing_nftpandamask', '6372'),
    ('clothing_nftpartyhat1', '5010'), ('clothing_nftpartyhat2', '5011'),
    ('clothing_nftpartyhat3', '5012'), ('clothing_nftpartyshades', '6521'),
    ('clothing_nftpartyshades1', '5007'), ('clothing_nftpartyshades2', '5008'),
    ('clothing_nftpartyshades3', '5009'), ('clothing_nftpenguin', '5066,5067'),
    ('clothing_nftpenguindress', '5068'), ('clothing_nftpenguinslippers', '5069'),
    ('clothing_nftpiercings', '5391,5392,5393'), ('clothing_nftpineapplehelmet', '6300'),
    ('clothing_nftpinkbeanie', '5511'), ('clothing_nftpinkbikini', '5262,5263,5264'),
    ('clothing_nftpinkhearteyes', '6024,6025'), ('clothing_nftpinkheartshades', '4988'),
    ('clothing_nftpinkrainboots', '5330'), ('clothing_nftpinkraincoat', '5324,5327'),
    ('clothing_nftpinkstarshades', '4987'), ('clothing_nftpinkswimshorts', '5266'),
    ('clothing_nftpinwheel', '6527'), ('clothing_nftpiranha', '6145'),
    ('clothing_nftplainshirt', '5856,5857'), ('clothing_nftplayingcardoutfit', '6097,6098,6099,6100'),
    ('clothing_nftpolarnight', '6289'), ('clothing_nftpopcorn', '6394,6395'),
    ('clothing_nftpopsicle1', '6129'), ('clothing_nftpopsicle2', '6130'),
    ('clothing_nftpopsicle3', '6131'), ('clothing_nftpossesseddoll', '5042'),
    ('clothing_nftpotpalm', '6146'), ('clothing_nftpumpkinbag', '5043'),
    ('clothing_nftpurse', '5616'), ('clothing_nftqueen', '5886'),
    ('clothing_nftqueenbee', '5591'), ('clothing_nftrainbowbikini', '6524,6525,6526'),
    ('clothing_nftrainbowpartyshades', '6522'), ('clothing_nftrainbowswimshorts', '6523'),
    ('clothing_nftraspberrybag', '5538'), ('clothing_nftraspberrycap', '5535'),
    ('clothing_nftraspberryshirt', '5526,5529,5546'), ('clothing_nftraspberryshoes', '5541'),
    ('clothing_nftrastasanta', '5103'), ('clothing_nftrastasantajacket', '5891,5892'),
    ('clothing_nftrastasantaprint', '6278,6317'), ('clothing_nftregalblackcrown', '6291'),
    ('clothing_nftreindeer', '5070,5071'), ('clothing_nftreindeerdress', '5072'),
    ('clothing_nftreindeerslippers', '5073'), ('clothing_nftrippedjeans', '5398'),
    ('clothing_nftrippedshorts', '5399'), ('clothing_nftrobotikcrown', '5797'),
    ('clothing_nftrockstar', '6252'), ('clothing_nftrollerskate', '6481'),
    ('clothing_nftrollerskatershades', '6472'), ('clothing_nftroundshades0', '5643'),
    ('clothing_nftroundshades1', '5644'), ('clothing_nftroundshades2', '5645'),
    ('clothing_nftroundshades3', '5646'), ('clothing_nftroundshades4', '5647'),
    ('clothing_nftrudolphprint', '6279,6315'), ('clothing_nftrudolphsnose', '6284'),
    ('clothing_nfts25glasses1', '6152'), ('clothing_nfts25glasses2', '6153'),
    ('clothing_nftsack', '5895'), ('clothing_nftsatyr', '6290'),
    ('clothing_nftsbboosters', '5568'), ('clothing_nftsbrangerjacket', '5572,5573'),
    ('clothing_nftsbraygun', '5569'), ('clothing_nftsbrocketpack', '5571'),
    ('clothing_nftsbvisor', '5570'), ('clothing_nftscarface', '6045,6046,6047,6048'),
    ('clothing_nftseahorsefloat', '6190'), ('clothing_nftseashellp1', '6185'),
    ('clothing_nftseashellp2', '6186'), ('clothing_nftseashellp3', '6187'),
    ('clothing_nftseashellp4', '6188'), ('clothing_nftshiba', '5609,5610,5611'),
    ('clothing_nftshortdreads', '5321'), ('clothing_nftshoulderdragon1', '6342'),
    ('clothing_nftshoulderdragon2', '6553'), ('clothing_nftsilkbandana', '5866'),
    ('clothing_nftsilvereyes', '6218,6219'), ('clothing_nftsilverjunglemask', '6484'),
    ('clothing_nftsilverskin', '6003,6004'), ('clothing_nftskeletonskin', '6248'),
    ('clothing_nftskullpendant', '5757'), ('clothing_nftskullsnroses1', '5394,5395'),
    ('clothing_nftskullsnroses2', '5396,5397'), ('clothing_nftsleepless', '5524,5525'),
    ('clothing_nftsmileyface', '6158,6159'), ('clothing_nftsnake', '6031'),
    ('clothing_nftsnakecrown', '5980'), ('clothing_nftsnaketeeth', '5975'),
    ('clothing_nftsnakeyearmark1', '5976'), ('clothing_nftsnakeyearmark2', '5977'),
    ('clothing_nftsnakeyearmark3', '5978'), ('clothing_nftsnakeyearmark4', '5979'),
    ('clothing_nftsneakers', '5868'), ('clothing_nftsneakers2', '5931'),
    ('clothing_nftsnowboard', '5574'), ('clothing_nftsnowmanbun', '6318'),
    ('clothing_nftsnowoperadress', '5333,5334'), ('clothing_nftsnowreindeerbackpack', '5352'),
    ('clothing_nftsoda', '6392,6393'), ('clothing_nftspdemon', '5825'),
    ('clothing_nftspringbrimhat', '5221'), ('clothing_nftsquirrel', '6442'),
    ('clothing_nftstarfacepaint', '6285'), ('clothing_nftstatue', '5430'),
    ('clothing_nftstrawberrybag', '5540'), ('clothing_nftstrawberrycap', '5537'),
    ('clothing_nftstrawberryshirt', '5528,5531,5548'), ('clothing_nftstrawberryshoes', '5543'),
    ('clothing_nftstrawhat', '6543'), ('clothing_nftstrawmouth', '6544'),
    ('clothing_nftstreetboy', '5590'), ('clothing_nftstreetgirl', '5589'),
    ('clothing_nftstripedtrousers', '5623'), ('clothing_nftsuitcase', '5796'),
    ('clothing_nftsummeranklet1', '5668'), ('clothing_nftsummeranklet2', '5669'),
    ('clothing_nftsummeranklet3', '5717'), ('clothing_nftsummeranklet4', '5718'),
    ('clothing_nftsummerbird', '5742'), ('clothing_nftsummercap1', '5723'),
    ('clothing_nftsummercap2', '5724'), ('clothing_nftsummercap3', '5725'),
    ('clothing_nftsummercap4', '5726'), ('clothing_nftsummercap5', '5727'),
    ('clothing_nftsummercap6', '5728'), ('clothing_nftsummercurly24', '5638'),
    ('clothing_nftsummercut24', '5637'), ('clothing_nftsummerflower1', '5648'),
    ('clothing_nftsummerflower2', '5649'), ('clothing_nftsummerflower3', '5650'),
    ('clothing_nftsummerflower4', '5651'), ('clothing_nftsummershirt1', '5652,5656'),
    ('clothing_nftsummershirt2', '5653,5657'), ('clothing_nftsummershirt3', '5654,5658'),
    ('clothing_nftsummershirt4', '5655,5659'), ('clothing_nftsummertop1', '5660,5664'),
    ('clothing_nftsummertop2', '5661,5665'), ('clothing_nftsummertop3', '5662,5666'),
    ('clothing_nftsummertop4', '5663,5667'), ('clothing_nftsun', '6144'),
    ('clothing_nftsunflowerbadge', '6018'), ('clothing_nftsunflowerdress', '5224,5225'),
    ('clothing_nftsunflowerheadacc', '6019'), ('clothing_nftsunflowershirt', '5222,5223'),
    ('clothing_nftsunflowershorts', '5226'), ('clothing_nftsuspenders', '6470'),
    ('clothing_nftswordnshiled', '6255'), ('clothing_nfttallcrown', '6080'),
    ('clothing_nfttangeberet', '5443'), ('clothing_nftteddymask', '6371'),
    ('clothing_nftthebigcap', '6076'), ('clothing_nfttintedglasses', '6447'),
    ('clothing_nfttoga', '5715,5716'), ('clothing_nfttophat', '4972'),
    ('clothing_nfttreasurechest', '6486'), ('clothing_nfttrench', '6079'),
    ('clothing_nfttropical', '4981,4982'), ('clothing_nfttropicalcrop', '4983,4984'),
    ('clothing_nfttubescarf', '6304'), ('clothing_nftturkeyhat', '6301'),
    ('clothing_nftumbrella', '6247,6251'), ('clothing_nftunicornhoodie', '5290,5291,5294'),
    ('clothing_nftunicornhorn', '5553'), ('clothing_nftunicornslippers', '5297'),
    ('clothing_nftutility', '6253'), ('clothing_nftvalentinesrose', '5996'),
    ('clothing_nftvivideyes', '5318,5319'), ('clothing_nftwarhammer', '6451'),
    ('clothing_nftwarpaint', '6232,6245'), ('clothing_nftwatermelonhelmet', '6197'),
    ('clothing_nftwesternhat', '6540'), ('clothing_nftwesternshirt', '6541,6542'),
    ('clothing_nftwesternvest', '6530,6531,6532'), ('clothing_nftwhitebeanie', '5510'),
    ('clothing_nftwinte25jacket', '6306,6307'), ('clothing_nftwinter', '5988,5992'),
    ('clothing_nftwinter1', '5989,5993'), ('clothing_nftwinter2', '5990,5994'),
    ('clothing_nftwinter25beanie', '6302'), ('clothing_nftwinter25boots', '6305'),
    ('clothing_nftwinter25pants', '6303'), ('clothing_nftwinter3', '5991,5995'),
    ('clothing_nftwinterblack', '5506,5507'), ('clothing_nftwinterbreeze', '5124'),
    ('clothing_nftwintercapbrown', '5132'), ('clothing_nftwintercapcheck', '5134'),
    ('clothing_nftwintercapwhite', '5133'), ('clothing_nftwintercoatbrown', '5126'),
    ('clothing_nftwintercoatcheck', '5128'), ('clothing_nftwintercoatwhite', '5127'),
    ('clothing_nftwintergreen', '5504,5505'), ('clothing_nftwinterpink', '5508,5509'),
    ('clothing_nftwinterweaves', '5125'), ('clothing_nftwizardsbeard', '6271,6272'),
    ('clothing_nftwizardsglasses', '6270'), ('clothing_nftwizardshaircut', '6269'),
    ('clothing_nftwizardshat', '6273,6277'), ('clothing_nftwizardsrobe', '6275'),
    ('clothing_nftxlogo', '5338'), ('clothing_nftxmasbonnieprint', '6280,6316'),
    ('clothing_nftxmasdress', '5064'), ('clothing_nftxmaslollipop', '6274'),
    ('clothing_nftxmaspants', '5062,5063'), ('clothing_nftxmasshoes', '5065'),
    ('clothing_nftxmassocks', '5896'), ('clothing_nftyellowrainboots', '5328'),
    ('clothing_nftyellowraincoat', '5322,5325'), ('clothing_nftyoyo', '6449'),
    ('clothing_nftyumekawa', '6035'), ('clothing_nftyummy', '5435');

-- ---------------------------------------------------------------- resolve against this hotel

UPDATE `tmp_col_products` p
JOIN `tmp_col_offers` o ON o.`classname` = p.`classname`
SET p.`emerald_price` = o.`emerald_price`, p.`shop_position` = o.`shop_position`;

UPDATE `tmp_col_products` p
SET p.`item_id` = (SELECT MIN(i.`id`) FROM `tmp_collectibles_items` i
                   WHERE i.`item_name` = p.`classname` AND i.`type` IN ('s', 'i'));
UPDATE `tmp_col_products` p
SET p.`item_type` = (SELECT i.`type` FROM `tmp_collectibles_items` i WHERE i.`id` = p.`item_id`),
    p.`is_clothing` = COALESCE((SELECT IF(i.`item_name` LIKE 'clothing\\_%' OR i.`interaction_type` = 'clothing', 1, 0)
                                FROM `tmp_collectibles_items` i WHERE i.`id` = p.`item_id`), 0);

UPDATE `tmp_col_relics` r
SET r.`base_id` = (SELECT MIN(i.`id`) FROM `tmp_collectibles_items` i
                   WHERE i.`item_name` = r.`base_classname` AND i.`type` = r.`base_type`),
    r.`twin_id` = (SELECT MIN(i.`id`) FROM `tmp_collectibles_items` i
                   WHERE i.`item_name` = r.`product_classname` AND i.`type` IN ('s', 'i'));
-- No nft_ furni in this hotel: the relic is a collectible of the furni itself.
UPDATE `tmp_col_relics` r SET r.`twin_id` = r.`base_id` WHERE r.`twin_id` IS NULL;
UPDATE `tmp_col_relics` r
SET r.`twin_type` = (SELECT i.`type` FROM `tmp_collectibles_items` i WHERE i.`id` = r.`twin_id`);

-- Furni missing from this hotel, or whose code the server would refuse (letters, digits, _ - . :, at
-- most 64 characters, '*' written as '.'), are skipped.
DELETE FROM `tmp_col_products`
WHERE `item_id` IS NULL
   OR REPLACE(`classname`, '*', '.') NOT REGEXP '^[A-Za-z0-9_.:-]{1,64}$';
DELETE FROM `tmp_col_relics`
WHERE `base_id` IS NULL
   OR `twin_type` IS NULL
   OR `twin_type` NOT IN ('s', 'i')
   OR REPLACE(`product_classname`, '*', '.') NOT REGEXP '^[A-Za-z0-9_.:-]{1,64}$';

-- ---------------------------------------------------------------- take back the first version's guesses

-- Its default-price shop offers (sort_order 1000, 50 emeralds) that Habbo does not sell.
UPDATE `collectible_products` p
SET p.`shop_enabled` = 0
WHERE @undo_earlier_guesses = 1
  AND p.`shop_enabled` = 1
  AND p.`sort_order` = 1000
  AND p.`emerald_price` = 50
  AND NOT EXISTS (
      SELECT 1 FROM `tmp_col_products` t
      WHERE t.`emerald_price` IS NOT NULL
        AND REPLACE(t.`classname`, '*', '.')
            = CONVERT(p.`product_code` USING utf8mb4) COLLATE utf8mb4_general_ci
  );

-- The three shop tiles it matched to the wrong furni, while they still carry its prices.
UPDATE `collectible_products` p
SET p.`shop_enabled` = 0
WHERE @undo_earlier_guesses = 1
  AND p.`shop_enabled` = 1
  AND ((p.`product_code` = 'nft_sc_wanddisplay' AND p.`emerald_price` = 50)
    OR (p.`product_code` = 'nft_sc26_wanddisplay' AND p.`emerald_price` = 75)
    OR (p.`product_code` = 'nft_h26_broomstickrack' AND p.`emerald_price` = 300));

-- Its single stamp pack: Habbo's ten packs below include the same 4 for 400.
UPDATE `collectible_mint_token_offers` o
SET o.`enabled` = 0
WHERE @undo_earlier_guesses = 1
  AND CONVERT(o.`product_code` USING utf8mb4) COLLATE utf8mb4_general_ci = 'curator_stamps';

-- ---------------------------------------------------------------- write

-- Sets with at least one product in this hotel. The name and order are OBSERVED; the completion XP is
-- a DEFAULT, only written when the set is created.
INSERT INTO `collectible_collections` (`collection_id`, `name`, `completion_xp`, `enabled`, `sort_order`)
SELECT s.`collection_id`, s.`name`, @set_completion_xp, 1, s.`sort_order`
FROM `tmp_col_sets` s
WHERE EXISTS (SELECT 1 FROM `tmp_col_products` p WHERE p.`collection_id` = s.`collection_id`)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `sort_order` = VALUES(`sort_order`);

-- Set products. product_type: 0 wall item, 1 floor item, 11 clothing; item_type_id: the items_base id.
-- Offers are put in the shop at Habbo's price; a product Habbo does not sell keeps what an operator set.
INSERT INTO `collectible_products`
    (`product_code`, `product_type`, `item_type_id`, `score`, `rarity`, `collection_id`,
     `emerald_price`, `shop_enabled`, `mint_limit`, `sort_order`)
SELECT REPLACE(p.`classname`, '*', '.'),
       CASE WHEN p.`item_type` = 'i' THEN 0 WHEN p.`is_clothing` = 1 THEN 11 ELSE 1 END,
       CAST(p.`item_id` AS CHAR), p.`score`, p.`rarity`, p.`collection_id`,
       COALESCE(p.`emerald_price`, 0), IF(p.`emerald_price` IS NULL, 0, 1), -1,
       COALESCE(p.`shop_position`, 100 + p.`set_position`)
FROM `tmp_col_products` p
ON DUPLICATE KEY UPDATE
    `product_type` = VALUES(`product_type`),
    `item_type_id` = VALUES(`item_type_id`),
    `score` = VALUES(`score`),
    `rarity` = VALUES(`rarity`),
    `collection_id` = VALUES(`collection_id`),
    `sort_order` = VALUES(`sort_order`),
    `emerald_price` = IF(VALUES(`shop_enabled`) = 1, VALUES(`emerald_price`), `collectible_products`.`emerald_price`),
    `mint_limit` = IF(VALUES(`shop_enabled`) = 1, -1, `collectible_products`.`mint_limit`),
    `shop_enabled` = IF(VALUES(`shop_enabled`) = 1, 1, `collectible_products`.`shop_enabled`);

-- Relic products: what minting gives, not sold in the shop. XP and rarity are DEFAULTS.
INSERT INTO `collectible_products`
    (`product_code`, `product_type`, `item_type_id`, `score`, `rarity`, `emerald_price`, `shop_enabled`,
     `mint_limit`, `sort_order`)
SELECT REPLACE(r.`product_classname`, '*', '.'),
       IF(r.`twin_type` = 'i', 0, 1),
       CAST(r.`twin_id` AS CHAR), @relic_score, @relic_rarity, 0, 0, -1, 500 + r.`relic_position`
FROM `tmp_col_relics` r
ON DUPLICATE KEY UPDATE
    `product_type` = VALUES(`product_type`),
    `item_type_id` = VALUES(`item_type_id`),
    `sort_order` = VALUES(`sort_order`);

-- The relics. Every relic gets a real end: the client treats an end of 0 as over (AIR
-- MintInventoryListTab.isMintPeriodExpired) and so does the server.
INSERT INTO `collectible_mintables` (`item_id`, `product_code`, `price`, `start_time`, `end_time`, `enabled`)
SELECT r.`base_id`, REPLACE(r.`product_classname`, '*', '.'), r.`stamp_price`,
       IF(@relic_window_days > 0, @now, r.`start_time`),
       IF(@relic_window_days > 0, @now + @relic_window_days * 86400, r.`end_time`),
       1
FROM `tmp_col_relics` r
ON DUPLICATE KEY UPDATE
    `price` = VALUES(`price`),
    `enabled` = 1,
    `start_time` = IF(@relic_window_reset = 1 OR `collectible_mintables`.`end_time` <= 0
                          OR `collectible_mintables`.`product_code` LIKE 'relic\\_%',
                      VALUES(`start_time`), `collectible_mintables`.`start_time`),
    `end_time` = IF(@relic_window_reset = 1 OR `collectible_mintables`.`end_time` <= 0
                        OR `collectible_mintables`.`product_code` LIKE 'relic\\_%',
                    VALUES(`end_time`), `collectible_mintables`.`end_time`),
    `product_code` = VALUES(`product_code`);

-- The first version's relics that Habbo does not offer.
UPDATE `collectible_mintables` m
SET m.`enabled` = 0
WHERE @undo_earlier_guesses = 1
  AND m.`product_code` LIKE 'relic\\_%';

-- Stamp packs, matched by product code; Habbo's offer id when it is free.
UPDATE `collectible_mint_token_offers` o
JOIN `tmp_col_stamp_packs` s
  ON s.`product_code` = CONVERT(o.`product_code` USING utf8mb4) COLLATE utf8mb4_general_ci
SET o.`silver_price` = s.`silver_price`, o.`amount` = s.`amount`, o.`enabled` = 1;

INSERT INTO `collectible_mint_token_offers` (`offer_id`, `product_code`, `silver_price`, `amount`, `enabled`)
SELECT IF(EXISTS (SELECT 1 FROM `collectible_mint_token_offers` x WHERE x.`offer_id` = s.`offer_id`),
          NULL, s.`offer_id`),
       s.`product_code`, s.`silver_price`, s.`amount`, 1
FROM `tmp_col_stamp_packs` s
WHERE NOT EXISTS (
    SELECT 1 FROM `collectible_mint_token_offers` o
    WHERE CONVERT(o.`product_code` USING utf8mb4) COLLATE utf8mb4_general_ci = s.`product_code`
)
ORDER BY s.`offer_id`;

-- Figure set ids of clothing products that have none yet (AIR's product info sends them, the hub
-- previews the clothing on the avatar with them): the furni's items_base customparams first, then
-- catalog_clothing, then the furnidata.
UPDATE `collectible_products` p
JOIN `tmp_collectibles_items` i
  ON i.`id` = CASE WHEN p.`item_type_id` REGEXP '^[0-9]{1,10}$' THEN CAST(p.`item_type_id` AS UNSIGNED) END
SET p.`figure_set_ids` = REPLACE(i.`customparams`, ' ', '')
WHERE p.`product_type` = 11
  AND p.`figure_set_ids` = ''
  AND REPLACE(i.`customparams`, ' ', '') REGEXP '^[0-9]+(,[0-9]+)*$'
  AND REPLACE(i.`customparams`, ' ', '') <> '0'
  AND CHAR_LENGTH(REPLACE(i.`customparams`, ' ', '')) <= 255;

SET @col_clothing_sql := IF(
    EXISTS (SELECT 1 FROM information_schema.`TABLES`
            WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'catalog_clothing'),
    'UPDATE `collectible_products` p
     JOIN `tmp_collectibles_items` i
       ON i.`id` = CASE WHEN p.`item_type_id` REGEXP ''^[0-9]{1,10}$'' THEN CAST(p.`item_type_id` AS UNSIGNED) END
     JOIN `catalog_clothing` c
       ON CONVERT(c.`name` USING utf8mb4) COLLATE utf8mb4_general_ci = i.`item_name`
     SET p.`figure_set_ids` = REPLACE(c.`setid`, '' '', '''')
     WHERE p.`product_type` = 11
       AND p.`figure_set_ids` = ''''
       AND REPLACE(c.`setid`, '' '', '''') REGEXP ''^[0-9]+(,[0-9]+)*$''
       AND CHAR_LENGTH(REPLACE(c.`setid`, '' '', '''')) <= 255',
    'DO 0');
PREPARE `col_clothing_stmt` FROM @col_clothing_sql;
EXECUTE `col_clothing_stmt`;
DEALLOCATE PREPARE `col_clothing_stmt`;

UPDATE `collectible_products` p
JOIN `tmp_collectibles_items` i
  ON i.`id` = CASE WHEN p.`item_type_id` REGEXP '^[0-9]{1,10}$' THEN CAST(p.`item_type_id` AS UNSIGNED) END
JOIN `tmp_col_figure_sets` f ON f.`classname` = i.`item_name`
SET p.`figure_set_ids` = f.`figure_set_ids`
WHERE p.`product_type` = 11
  AND p.`figure_set_ids` = '';

DROP TEMPORARY TABLE IF EXISTS `tmp_col_figure_sets`;
DROP TEMPORARY TABLE IF EXISTS `tmp_col_stamp_packs`;
DROP TEMPORARY TABLE IF EXISTS `tmp_col_relics`;
DROP TEMPORARY TABLE IF EXISTS `tmp_col_offers`;
DROP TEMPORARY TABLE IF EXISTS `tmp_col_products`;
DROP TEMPORARY TABLE IF EXISTS `tmp_col_sets`;
DROP TEMPORARY TABLE IF EXISTS `tmp_collectibles_items`;

-- Check: the shop and relics tabs as the server will send them, then the totals.
SELECT 'shop' AS tab, p.`sort_order`, p.`product_code`, p.`emerald_price` AS price, 'emeralds' AS currency,
       b.`id` AS items_base_id, b.`sprite_id`, NULL AS days_left
FROM `collectible_products` p
JOIN `items_base` b ON CAST(b.`id` AS CHAR) = p.`item_type_id`
WHERE p.`shop_enabled` = 1 AND p.`emerald_price` > 0
UNION ALL
SELECT 'relic', NULL, m.`product_code`, m.`price`, 'stamps', m.`item_id`, b.`sprite_id`,
       FLOOR((m.`end_time` - UNIX_TIMESTAMP()) / 86400)
FROM `collectible_mintables` m
JOIN `items_base` b ON b.`id` = m.`item_id`
WHERE m.`enabled` = 1
UNION ALL
SELECT 'stamps', o.`offer_id`, o.`product_code`, o.`silver_price`, CONCAT('silver for ', o.`amount`), NULL, NULL, NULL
FROM `collectible_mint_token_offers` o
WHERE o.`enabled` = 1
ORDER BY tab, sort_order, product_code;

SELECT (SELECT COUNT(*) FROM `collectible_collections` WHERE `enabled` = 1) AS sets,
       (SELECT COUNT(*) FROM `collectible_products` WHERE `collection_id` IS NOT NULL) AS set_products,
       (SELECT COUNT(*) FROM `collectible_products` WHERE `shop_enabled` = 1 AND `emerald_price` > 0) AS shop_offers,
       (SELECT COUNT(*) FROM `collectible_mintables` WHERE `enabled` = 1) AS relics,
       (SELECT COUNT(*) FROM `collectible_mint_token_offers` WHERE `enabled` = 1) AS stamp_packs,
       (SELECT COUNT(*) FROM `collectible_products` WHERE `product_type` = 11) AS clothing,
       (SELECT COUNT(*) FROM `collectible_products` WHERE `product_type` = 11 AND `figure_set_ids` <> '') AS clothing_with_figure_sets;
