-- Phase-1 advanced wired furni: point the (previously inert, 'default')
-- items_base rows at their newly-registered interaction classes so the
-- emulator loads the real wired class instead of InteractionDefault.
-- Same pattern as 013/014/015. Requires an emulator restart (items_base
-- is read at startup). Idempotent (only updates rows that currently differ).
--   wf_xtra_mov_curve          -> WiredExtraMovementCurve   (add-on, code 97)
--   wf_xtra_var_time_util      -> WiredExtraTimeUtilities    (add-on, code 98)
--   wf_act_move_furni_as_group -> WiredEffectMoveFurniAsGroup (effect, code 95)
--   wf_slc_remote              -> WiredEffectRemoteSelector   (selector, code 96)

UPDATE items_base SET interaction_type = 'wf_xtra_mov_curve'
    WHERE item_name = 'wf_xtra_mov_curve'          AND interaction_type <> 'wf_xtra_mov_curve';

UPDATE items_base SET interaction_type = 'wf_xtra_var_time_util'
    WHERE item_name = 'wf_xtra_var_time_util'      AND interaction_type <> 'wf_xtra_var_time_util';

UPDATE items_base SET interaction_type = 'wf_act_move_furni_as_group'
    WHERE item_name = 'wf_act_move_furni_as_group' AND interaction_type <> 'wf_act_move_furni_as_group';

UPDATE items_base SET interaction_type = 'wf_slc_remote'
    WHERE item_name = 'wf_slc_remote'              AND interaction_type <> 'wf_slc_remote';

-- Teleport-to-room: the official furni exist in furnidata but were inert; point them at the
-- existing WiredEffectForwardUserToRoom (reuses the SHOW_MESSAGE dialog, text field = target room id).
UPDATE items_base SET interaction_type = 'wf_act_teleport_to_room'
    WHERE item_name = 'wf_act_teleport_to_room'    AND interaction_type <> 'wf_act_teleport_to_room';

UPDATE items_base SET interaction_type = 'wf_act_tele_room'
    WHERE item_name = 'wf_act_tele_room'           AND interaction_type <> 'wf_act_tele_room';
