-- Purchasable chat styles (AIR "chat_style" catalog products).
--
-- Sells every chat style that ui-config.json marks with "isPurchasable": true:
--   * one catalog page "Chatballonnen" (caption_save chat_styles), created once;
--   * one offer per style on that page (catalog_name chat_style_<id>, no furni, item_ids '');
--   * chat_bubbles.catalog_item_id pointing at that offer, which is what makes the server
--     sell, grant and enforce the style.
--
-- Styles 5-7 are for everyone; the others are HC styles in ui-config ("isHcOnly": true), so
-- their offers are club_only. A chat_bubbles row for a built-in style (below 54) replaces the
-- server's built-in definition, so each row repeats the built-in name, overridable and
-- triggers_talking_furniture values from RoomChatMessageBubbles; only catalog_item_id is new.
--
-- Works whatever charset the catalog tables use (latin1 from old dumps or utf8mb4).
--
-- Safe to run more than once: the page and offers are found again by caption_save and
-- catalog_name, and existing rows are updated instead of duplicated.
--
-- Adjust the settings below, run the script, then :update_catalogue and :update_chat_bubbles
-- (or restart the emulator).

SET @parent_page_id := -1;  -- catalog_pages.id of the parent tab; -1 puts the page at the top level
SET @price_credits := 5;
SET @price_points := 0;
SET @points_type := 0;      -- 0 duckets, 5 diamonds (only used when @price_points > 0)
SET @page_min_rank := 1;

DROP TEMPORARY TABLE IF EXISTS `tmp_chat_style_offers`;
CREATE TEMPORARY TABLE `tmp_chat_style_offers` (
    `style_id` INT NOT NULL PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL,
    `overridable` TINYINT(1) NOT NULL,
    `triggers_talking_furniture` TINYINT(1) NOT NULL,
    `hc_only` TINYINT(1) NOT NULL
);

INSERT INTO `tmp_chat_style_offers` (`style_id`, `name`, `overridable`, `triggers_talking_furniture`, `hc_only`) VALUES
    (5, 'YELLOW', 1, 1, 0),
    (6, 'GREEN', 1, 1, 0),
    (7, 'BLACK', 1, 1, 0),
    (9, 'ZOMBIE_ARM', 1, 0, 1),
    (10, 'SKELETON', 1, 0, 1),
    (11, 'LIGHT_BLUE', 1, 1, 1),
    (12, 'PINK', 1, 1, 1),
    (13, 'PURPLE', 1, 1, 1),
    (14, 'DARK_YELLOW', 1, 1, 1),
    (15, 'DARK_BLUE', 1, 1, 1),
    (16, 'HEARTS', 1, 1, 1),
    (17, 'ROSES', 1, 1, 1),
    (18, 'UNUSED', 1, 1, 1),
    (19, 'PIG', 1, 1, 1),
    (20, 'DOG', 1, 1, 1),
    (21, 'BLAZE_IT', 1, 1, 1),
    (22, 'DRAGON', 1, 1, 1),
    (24, 'BATS', 1, 0, 1),
    (25, 'MESSENGER', 1, 0, 1),
    (26, 'STEAMPUNK', 1, 0, 1),
    (27, 'THUNDER', 1, 1, 1),
    (28, 'PARROT', 0, 0, 1),
    (29, 'PIRATE', 0, 0, 1),
    (32, 'SCARY_THING', 1, 0, 1),
    (35, 'GOAT', 1, 0, 1),
    (36, 'SANTA', 1, 0, 1),
    (38, 'RADIO', 1, 0, 1);

-- The page.
INSERT INTO `catalog_pages` (`parent_id`, `caption_save`, `caption`, `page_layout`, `icon_image`, `min_rank`, `order_num`, `visible`, `enabled`, `page_headline`, `page_teaser`, `page_text1`, `page_text2`)
SELECT @parent_page_id, 'chat_styles', 'Chatballonnen', 'default_3x3', 1, @page_min_rank, 99, '1', '1', '', '',
       'Geef je berichten een eigen stijl met een nieuwe chatballon.', 'Gekochte ballonnen kies je in de chatbalk.'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `catalog_pages` WHERE `caption_save` = 'chat_styles');

SET @chat_styles_page_id := (SELECT MIN(`id`) FROM `catalog_pages` WHERE `caption_save` = 'chat_styles');

-- One offer per style.
INSERT INTO `catalog_items` (`item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`, `amount`, `order_number`, `offer_id`, `extradata`, `have_offer`, `club_only`)
SELECT '', @chat_styles_page_id, CONCAT('chat_style_', s.`style_id`), @price_credits, @price_points, @points_type, 1,
       s.`style_id`, -1, '', '1', IF(s.`hc_only` = 1, '1', '0')
FROM `tmp_chat_style_offers` s
WHERE NOT EXISTS (
    SELECT 1 FROM `catalog_items` c
    WHERE c.`page_id` = @chat_styles_page_id
      AND CONVERT(c.`catalog_name` USING utf8mb4) COLLATE utf8mb4_general_ci
          = CONVERT(CONCAT('chat_style_', s.`style_id`) USING utf8mb4) COLLATE utf8mb4_general_ci
);

-- have_offer must be '1': the client only lets a player buy an offer that has it. Earlier runs of
-- this script inserted '0', so repair those offers too.
UPDATE `catalog_items`
SET `have_offer` = '1'
WHERE `page_id` = @chat_styles_page_id
  AND `catalog_name` LIKE 'chat\\_style\\_%'
  AND `have_offer` = '0';

-- Point each style at its offer.
INSERT INTO `chat_bubbles` (`type`, `name`, `permission`, `overridable`, `triggers_talking_furniture`, `catalog_item_id`)
SELECT s.`style_id`, s.`name`, '', s.`overridable`, s.`triggers_talking_furniture`, c.`id`
FROM `tmp_chat_style_offers` s
JOIN `catalog_items` c
  ON c.`page_id` = @chat_styles_page_id
 AND CONVERT(c.`catalog_name` USING utf8mb4) COLLATE utf8mb4_general_ci
          = CONVERT(CONCAT('chat_style_', s.`style_id`) USING utf8mb4) COLLATE utf8mb4_general_ci
ON DUPLICATE KEY UPDATE `catalog_item_id` = VALUES(`catalog_item_id`);

DROP TEMPORARY TABLE IF EXISTS `tmp_chat_style_offers`;

-- Check: every purchasable style with its offer.
SELECT b.`type` AS style_id, b.`name`, b.`catalog_item_id`, c.`catalog_name`, c.`cost_credits`, c.`club_only`
FROM `chat_bubbles` b
JOIN `catalog_items` c ON c.`id` = b.`catalog_item_id`
ORDER BY b.`type`;
