-- Deferred Phase-1 furni: spawn / remove a furni in the room.
-- Point the inert items_base rows at their new classes. Pattern 013-019.
-- Emulator restart required. Idempotent.
--   wf_act_place_furni  -> WiredEffectPlaceFurni   (effect, code 106) — mints a base-type furni onto the floor
--   wf_act_remove_furni -> WiredEffectRemoveFurni  (effect, code 107) — picks up / deletes the selected furni

UPDATE items_base SET interaction_type = 'wf_act_place_furni'
    WHERE item_name = 'wf_act_place_furni'  AND interaction_type <> 'wf_act_place_furni';

UPDATE items_base SET interaction_type = 'wf_act_remove_furni'
    WHERE item_name = 'wf_act_remove_furni' AND interaction_type <> 'wf_act_remove_furni';
