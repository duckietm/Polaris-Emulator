CREATE TEMPORARY TABLE `catalog_sound_offer_reference_repair` (
    `catalog_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL PRIMARY KEY,
    `song_id` int unsigned NOT NULL,
    `code` varchar(128) NOT NULL
);

INSERT INTO `catalog_sound_offer_reference_repair` (`catalog_name`, `song_id`, `code`) VALUES
    ('SONG LostMyTapesAtGoa', 23, 'lost_my_tapes_at_goa'),
    ('SONG EpicFlail', 21, 'epic_flail'),
    ('SONG ElectricPixels', 25, 'electric_pixels'),
    ('SONG Xmas11', 26, 'xmas_2011'),
    ('SONG Xmas2011', 26, 'xmas_2011'),
    ('SONG WhoDaresStacks', 27, 'who_dares_stacks'),
    ('SONG GalacticDisco', 24, 'galactic_disco'),
    ('SONG AlleyCatInTrouble', 22, 'alley_cat_in_trouble'),
    ('SONG Trax_1', 15, 'party_trax'),
    ('SONG double_peks', 14, 'double_peks'),
    ('SONG Trax_2', 20, 'chilled_trax'),
    ('SONG Weirdodo', 13, 'weirdodo'),
    ('SONG Haadolocknloll', 12, 'haadolocknloll'),
    ('SONG TeemuP1', 11, 'good_trade'),
    ('SONG TeemuP2', 1, 'ballad_of_bonnie'),
    ('SONG bossanova', 2, 'bossa_nova'),
    ('SONG disco_extreme', 10, 'disco_extreme'),
    ('SONG klubhaus', 9, 'klub_haus'),
    ('SONG limbertake', 8, 'limber_take'),
    ('SONG miamimiamor', 7, 'miami_miamor'),
    ('SONG new_song', 6, 'gold_coin_digger'),
    ('SONG park_adventure', 18, 'park_adventure'),
    ('SONG ParkAdventure', 18, 'park_adventure'),
    ('SONG pianissimo', 4, 'pianissimo'),
    ('SONG RnB_Swat_Teem', 17, 'rnb_swat_teem');

UPDATE `catalog_items` AS offer
INNER JOIN `catalog_sound_offer_reference_repair` AS repair
    ON repair.`catalog_name` = offer.`catalog_name`
INNER JOIN `soundtracks` AS soundtrack
    ON soundtrack.`id` = repair.`song_id`
SET offer.`song_id` = IF(offer.`song_id` = 0, repair.`song_id`, offer.`song_id`),
    offer.`extradata` = IF(TRIM(offer.`extradata`) = '', soundtrack.`code`, offer.`extradata`)
WHERE offer.`song_id` = 0 OR TRIM(offer.`extradata`) = '';

UPDATE `catalog_version_offers` AS offer
INNER JOIN `catalog_sound_offer_reference_repair` AS repair
    ON repair.`catalog_name` = offer.`catalog_name`
INNER JOIN `soundtracks` AS soundtrack
    ON soundtrack.`id` = repair.`song_id`
SET offer.`song_id` = IF(offer.`song_id` = 0, repair.`song_id`, offer.`song_id`),
    offer.`extradata` = IF(TRIM(offer.`extradata`) = '', soundtrack.`code`, offer.`extradata`)
WHERE offer.`song_id` = 0 OR TRIM(offer.`extradata`) = '';

DROP TEMPORARY TABLE `catalog_sound_offer_reference_repair`;
