-- Cross-referencing the habbofurni.com "wired" catalogue (186 furni) against
-- next.items_base found 8 wired furni that have a WORKING, REGISTERED interaction class
-- in ItemManager.loadItemInteractions() but whose items_base interaction_type is wrong
-- (mostly 'default' -> InteractionDefault = inert). Same class of fix as 013/014: point
-- the row at its registered interaction name. Requires an emulator restart (items_base
-- is read at startup). Idempotent (only updates rows that currently differ).
--   wf_xtra_anim_time            -> WiredExtraAnimationTime      (was 'default')
--   wf_xtra_filter_users         -> WiredExtraFilterUser         (was 'default')
--   wf_xtra_mov_carry_users      -> WiredExtraMoveCarryUsers      (was 'default')
--   wf_xtra_mov_physics          -> WiredExtraMovePhysics         (was 'default')
--   wf_xtra_text_input_variable  -> WiredExtraTextInputVariable   (was 'default')
--   wf_antenna1 / wf_antenna2    -> 'antenna' (InteractionDefault); the signal system
--                                   recognises antenna furni by the interaction NAME 'antenna'
--   wf_act_unfreeze              -> WiredEffectUnfreeze (was the wrong 'wf_act_give_prefix')

UPDATE items_base SET interaction_type = 'wf_xtra_anim_time'
    WHERE item_name = 'wf_xtra_anim_time'           AND interaction_type <> 'wf_xtra_anim_time';

UPDATE items_base SET interaction_type = 'wf_xtra_filter_users'
    WHERE item_name = 'wf_xtra_filter_users'        AND interaction_type <> 'wf_xtra_filter_users';

UPDATE items_base SET interaction_type = 'wf_xtra_mov_carry_users'
    WHERE item_name = 'wf_xtra_mov_carry_users'     AND interaction_type <> 'wf_xtra_mov_carry_users';

UPDATE items_base SET interaction_type = 'wf_xtra_mov_physics'
    WHERE item_name = 'wf_xtra_mov_physics'         AND interaction_type <> 'wf_xtra_mov_physics';

UPDATE items_base SET interaction_type = 'wf_xtra_text_input_variable'
    WHERE item_name = 'wf_xtra_text_input_variable' AND interaction_type <> 'wf_xtra_text_input_variable';

UPDATE items_base SET interaction_type = 'antenna'
    WHERE item_name IN ('wf_antenna1', 'wf_antenna2') AND interaction_type <> 'antenna';

UPDATE items_base SET interaction_type = 'wf_act_unfreeze'
    WHERE item_name = 'wf_act_unfreeze'             AND interaction_type <> 'wf_act_unfreeze';
