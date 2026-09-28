-- Venetian water and stackable water are water furni: their pieces join into one pool like
-- bw_water, and pets can swim in them. Only rows still on the default interaction change, so a
-- hotel that set its own interaction keeps it.
UPDATE `items_base`
SET `interaction_type` = 'water'
WHERE `item_name` IN ('val13_water', 'stackable_water')
  AND (`interaction_type` = 'default' OR `interaction_type` = '');
