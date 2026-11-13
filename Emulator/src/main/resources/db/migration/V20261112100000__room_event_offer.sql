-- A room event page (layout roomads) without a catalogue item has nothing to sell: the client sent no offer and the
-- purchase was refused without an answer. Every such page gets the default offer, 100 duckets for 120 minutes.
-- Pages that already have an item are left alone, so the statement is idempotent and keeps a hotel's own price.
--
-- item_ids only has to name a real furni: none is handed out for a room event, but an item whose item_ids is 0 is
-- skipped when the catalogue loads. A hotel without any furni gets no row.

INSERT INTO `catalog_items` (`item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`, `amount`)
SELECT CAST(furni.`id` AS CHAR), page.`id`, 'room_ad_plus_badge', 0, 100, 0, 1
FROM `catalog_pages` page
JOIN (SELECT MIN(`id`) AS `id` FROM `items_base`) furni ON furni.`id` IS NOT NULL
WHERE page.`page_layout` = 'roomads'
  AND NOT EXISTS (SELECT 1 FROM `catalog_items` existing WHERE existing.`page_id` = page.`id`);
