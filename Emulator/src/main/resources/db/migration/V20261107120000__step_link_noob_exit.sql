-- room_noob_exit gets the "step_link" interaction: a user who steps on it is sent the link kept in the placed
-- furni's extra_data, as an in-client link (for example navigator/goto/123025756 to send a new user to that room).
-- Set the link per placed exit in items.extra_data. Looked up by item_name, and only a furni still on the default
-- interaction is changed, so a hotel's own choice is kept.

UPDATE `items_base`
SET `interaction_type` = 'step_link',
    `allow_walk` = '1'
WHERE `item_name` = 'room_noob_exit'
  AND `interaction_type` IN ('default', '');
