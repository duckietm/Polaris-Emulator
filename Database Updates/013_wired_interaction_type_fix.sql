-- =====================================================================
-- 013_wired_interaction_type_fix.sql
-- =====================================================================
-- Many WIRED furni (selectors wf_slc_*, variables wf_var_*, signals,
-- extras wf_xtra_*, and a few effects/conditions/triggers) exist in
-- items_base with the correct item_name (e.g. 'wf_slc_users_area') but
-- their interaction_type is 'default'. The emulator's name fallback
-- (Item.java) only re-resolves when the PUBLIC_NAME starts with 'wf_',
-- and these rows have a human public_name ("WIRED Selector: ..."), so the
-- fallback never fires and the furni load as InteractionDefault — i.e. the
-- whole selector / variable / signal wired subsystem is unplaceable.
--
-- Fix: set interaction_type = item_name for the affected rows, restricted
-- to item_names that are REAL registered wired interaction types (so no
-- unknown type is ever written). Idempotent: re-running matches nothing
-- once interaction_type is no longer 'default'.
--
-- Affects 59 rows (one furni per type) across the 59 wired types listed below.
-- (Of the 172 'wf_%'-named rows currently at 'default', the other 113 are
--  unregistered names like wf_button_*/wf_floor_* and are intentionally left alone.)
-- Back up items_base before running.
-- =====================================================================

UPDATE items_base
SET interaction_type = item_name
WHERE interaction_type = 'default'
  AND item_name IN (
    'wf_act_add_tag',
    'wf_act_change_var_val',
    'wf_act_freeze_habbo',
    'wf_act_give_var',
    'wf_act_neg_send_signal',
    'wf_act_remove_var',
    'wf_act_send_signal',
    'wf_act_set_altitude',
    'wf_act_toggle_to_rnd',
    'wf_act_unfreeze_habbo',
    'wf_cnd_has_var',
    'wf_cnd_neg_has_var',
    'wf_cnd_not_triggerer_match',
    'wf_cnd_not_user_performs_action',
    'wf_cnd_slc_quantity',
    'wf_cnd_team_has_rank',
    'wf_cnd_team_has_score',
    'wf_cnd_triggerer_match',
    'wf_cnd_user_performs_action',
    'wf_cnd_valid_moves',
    'wf_cnd_var_age_match',
    'wf_cnd_var_val_match',
    'wf_slc_furni_altitude',
    'wf_slc_furni_area',
    'wf_slc_furni_bytype',
    'wf_slc_furni_neighborhood',
    'wf_slc_furni_onfurni',
    'wf_slc_furni_picks',
    'wf_slc_furni_signal',
    'wf_slc_furni_with_var',
    'wf_slc_users_area',
    'wf_slc_users_byaction',
    'wf_slc_users_byname',
    'wf_slc_users_bytype',
    'wf_slc_users_group',
    'wf_slc_users_handitem',
    'wf_slc_users_neighborhood',
    'wf_slc_users_onfurni',
    'wf_slc_users_signal',
    'wf_slc_users_team',
    'wf_slc_users_with_var',
    'wf_trg_recv_signal',
    'wf_trg_stuff_state',
    'wf_trg_user_performs_action',
    'wf_trg_var_changed',
    'wf_var_context',
    'wf_var_echo',
    'wf_var_furni',
    'wf_var_reference',
    'wf_var_room',
    'wf_var_user',
    'wf_xtra_filter_furni',
    'wf_xtra_filter_furni_by_var',
    'wf_xtra_filter_users_by_var',
    'wf_xtra_text_output_furni_name',
    'wf_xtra_text_output_username',
    'wf_xtra_text_output_variable',
    'wf_xtra_var_lvlup_system',
    'wf_xtra_var_text_connector'
);
