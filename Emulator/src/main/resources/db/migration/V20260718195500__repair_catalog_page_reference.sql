-- The canonical base dump contains one Gothic Cafe item with a page-id typo:
-- every other item in the collection uses page 749, while this row used the
-- unrelated/missing page 769. Only the exact untouched seed row is repaired;
-- operator-customized catalog placement remains authoritative.
UPDATE `catalog_items`
SET `page_id` = 749
WHERE `id` = 10700
  AND `item_ids` = '11289'
  AND `catalog_name` = 'gothiccafe_c20_creamrolls'
  AND `page_id` = 769;
