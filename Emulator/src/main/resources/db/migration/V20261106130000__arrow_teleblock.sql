-- The public-room arrow (room_wl15_teleblock) gets the "arrow" interaction: a user whose walk ends on it is put on
-- its linked partner at once (in the same room, or by entering the partner's room on it), without the door
-- animation and wait of a teleport tile. Walking over it does nothing. Arrows are linked as teleport pairs in
-- items_teleports; a pair bought from the catalogue is linked when it is bought. Looked up by item_name, and only a
-- furni still on the old teleport tile interaction is changed, so a hotel's own choice is kept.

UPDATE `items_base`
SET `interaction_type` = 'arrow'
WHERE `item_name` = 'room_wl15_teleblock'
  AND `interaction_type` IN ('teleporttile', 'default', '');
