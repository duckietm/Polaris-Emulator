-- Wired movement animation add-on.
-- Points an existing wf_xtra_mov_animation furni row at its registered interaction,
-- when the furni exists in the active furnidata/items_base set.
UPDATE items_base SET interaction_type = 'wf_xtra_mov_animation'
    WHERE item_name = 'wf_xtra_mov_animation' AND interaction_type <> 'wf_xtra_mov_animation';
