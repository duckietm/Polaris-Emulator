-- Reconcile the historical Items_Base wired mappings for adopted hotels. Fresh
-- databases already contain most of these values in the baseline, but adopted
-- Arcturus/Polaris databases skip that baseline data import. Flyway records this
-- migration after it succeeds, so the repair is applied once and never on every
-- emulator start.
--
-- Prefer item_name because it is the stable furnidata classname. public_name is
-- retained as a fallback for older dumps where the historical repair used it as
-- the identifier. If both columns are known identifiers, item_name wins.
--
-- The current Polaris baseline supersedes eight targets from the historical
-- source. Keep Glowball's canonical interaction, route the two legacy badge
-- classnames to the newer ownership conditions, and preserve the five wired
-- implementations that replaced historical default mappings.
--
-- These are emulator-owned reference rows. Reconcile every stale value once,
-- as the source updates did, then rely on Flyway history so operator overrides
-- made after this migration are never rewritten on later starts.

CREATE TEMPORARY TABLE `polaris_items_base_interaction_repair_20260822` (
    `item_identifier` VARCHAR(70) NOT NULL,
    `interaction_type` VARCHAR(500) NOT NULL,
    PRIMARY KEY (`item_identifier`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_interaction_repair_20260822`
    (`item_identifier`, `interaction_type`)
VALUES
    ('wf_cnd_time_more_than', 'wf_cnd_time_more_than'),
    ('wf_cnd_time_less_than', 'wf_cnd_time_less_than'),
    ('wf_act_give_reward', 'wf_act_give_reward'),
    ('wf_act_call_stacks', 'wf_act_call_stacks'),
    ('wf_act_neg_call_stack', 'wf_act_neg_call_stack'),
    ('wf_act_neg_call_stacks', 'wf_act_neg_call_stacks'),
    ('wf_maze', 'default'),
    ('wf_act_give_score_tm', 'wf_act_give_score_tm'),
    ('wf_act_move_to_dir', 'wf_act_move_to_dir'),
    ('wf_act_leave_team', 'wf_act_leave_team'),
    ('wf_cnd_actor_in_team', 'wf_cnd_actor_in_team'),
    ('wf_act_flee', 'wf_act_flee'),
    ('wf_act_join_team', 'wf_act_join_team'),
    ('wf_cnd_not_in_team', 'wf_cnd_not_in_team'),
    ('wf_cnd_not_furni_on', 'wf_cnd_not_furni_on'),
    ('wf_cnd_stuff_is', 'wf_cnd_stuff_is'),
    ('wf_cnd_not_stuff_is', 'wf_cnd_not_stuff_is'),
    ('wf_cnd_date_rng_active', 'wf_cnd_date_rng_active'),
    ('wf_act_bot_clothes', 'wf_act_bot_clothes'),
    ('wf_act_bot_teleport', 'wf_act_bot_teleport'),
    ('wf_act_bot_follow_avatar', 'wf_act_bot_follow_avatar'),
    ('wf_act_bot_give_handitem', 'wf_act_bot_give_handitem'),
    ('wf_act_bot_move', 'wf_act_bot_move'),
    ('wf_cnd_has_handitem', 'wf_cnd_has_handitem'),
    ('wf_act_bot_talk_to_avatar', 'wf_act_bot_talk_to_avatar'),
    ('wf_trg_bot_reached_avtr', 'wf_trg_bot_reached_avtr'),
    ('wf_act_bot_talk', 'wf_act_bot_talk'),
    ('wf_act_move_rotate', 'wf_act_move_rotate'),
    ('wf_wire2', 'default'),
    ('wf_floor_switch2', 'switch'),
    ('wf_trg_state_changed', 'wf_trg_state_changed'),
    ('wf_xtra_random', 'wf_xtra_random'),
    ('wf_xtra_unseen', 'wf_xtra_unseen'),
    ('wf_trg_periodically', 'wf_trg_periodically'),
    ('wf_pyramid', 'pyramid'),
    ('wf_trg_score_achieved', 'wf_trg_score_achieved'),
    ('wf_act_teleport_to', 'wf_act_teleport_to'),
    ('wf_trg_says_something', 'wf_trg_says_something'),
    ('wf_wire4', 'default'),
    ('wf_trg_walks_off_furni', 'wf_trg_walks_off_furni'),
    ('wf_trg_at_given_time', 'wf_trg_at_given_time'),
    ('wf_trg_game_ends', 'wf_trg_game_ends'),
    ('wf_act_show_message', 'wf_act_show_message'),
    ('wf_trg_collision', 'wf_trg_collision'),
    ('wf_trg_enter_room', 'wf_trg_enter_room'),
    ('wf_act_toggle_state', 'wf_act_toggle_state'),
    ('wf_firegate', 'gate'),
    ('wf_ringplate', 'pressureplate'),
    ('wf_pressureplate', 'pressureplate'),
    ('wf_glowball', 'glowball'),
    ('wf_act_reset_timers', 'wf_act_reset_timers'),
    ('wf_cnd_furnis_hv_avtrs', 'wf_cnd_furnis_hv_avtrs'),
    ('wf_arrowplate', 'pressureplate'),
    ('wf_cnd_trggrer_on_frn', 'wf_cnd_trggrer_on_frn'),
    ('wf_wire1', 'default'),
    ('wf_act_give_score', 'wf_act_give_score'),
    ('wf_wire3', 'default'),
    ('wf_glassdoor', 'gate'),
    ('wf_act_match_to_sshot', 'wf_act_match_to_sshot'),
    ('wf_floor_switch1', 'switch'),
    ('wf_trg_game_starts', 'wf_trg_game_starts'),
    ('wf_trg_walks_on_furni', 'wf_trg_walks_on_furni'),
    ('wf_cnd_actor_in_group', 'wf_cnd_actor_in_group'),
    ('wf_cnd_not_in_group', 'wf_cnd_not_in_group'),
    ('wf_cnd_not_trggrer_on', 'wf_cnd_not_trggrer_on'),
    ('wf_cnd_not_hv_avtrs', 'wf_cnd_not_hv_avtrs'),
    ('wf_cnd_user_count_in', 'wf_cnd_user_count_in'),
    ('wf_cnd_not_user_count', 'wf_cnd_not_user_count'),
    ('wf_cnd_wearing_effect', 'wf_cnd_wearing_effect'),
    ('wf_cnd_not_wearing_fx', 'wf_cnd_not_wearing_fx'),
    ('wf_cnd_wearing_badge', 'wf_cnd_habbo_owns_badge'),
    ('wf_cnd_not_wearing_b', 'wf_cnd_not_habbo_owns_badge'),
    ('wf_act_kick_user', 'wf_act_kick_user'),
    ('wf_act_mute_triggerer', 'wf_act_mute_triggerer'),
    ('wf_cnd_match_snapshot', 'wf_cnd_match_snapshot'),
    ('wf_cnd_not_match_snap', 'wf_cnd_not_match_snap'),
    ('wf_blob', 'wf_blob'),
    ('wf_blob2', 'wf_blob'),
    ('wf_box', 'puzzle_box'),
    ('wf_cnd_has_furni_on', 'wf_cnd_has_furni_on'),
    ('wf_act_super_wired', 'wf_act_super_wired'),
    ('wf_cnd_super_wired', 'wf_cnd_super_wired'),
    ('wf_trg_period_long', 'wf_trg_period_long'),
    ('wf_trg_bot_reached_stf', 'wf_trg_bot_reached_stf'),
    ('wf_act_chase', 'wf_act_chase'),
    ('wf_act_move_furni_to', 'wf_act_move_furni_to'),
    ('wf_act_toggle_to_rnd', 'wf_act_toggle_to_rnd'),
    ('wf_blob2_vis', 'wf_blob'),
    ('wf_blob_invis', 'wf_blob'),
    ('wf_trg_at_time_long', 'wf_trg_at_time_long'),
    ('wf_act_control_clock', 'wf_act_control_clock'),
    ('wf_game_upcounter1', 'game_upcounter'),
    ('wf_game_upcounter2', 'game_upcounter'),
    ('wf_trg_clock_counter', 'wf_trg_clock_counter'),
    ('wf_xtra_or_eval', 'wf_xtra_or_eval'),
    ('wf_test_act', 'default'),
    ('wf_test_cnd', 'default'),
    ('wf_test_trg', 'default'),
    ('wf_test_xtra', 'default'),
    ('wf_cnd_counter_time_matches', 'wf_cnd_counter_time_matches'),
    ('wf_cnd_match_date', 'wf_cnd_match_date'),
    ('wf_cnd_match_time', 'wf_cnd_match_time'),
    ('wf_cnd_not_has_handitem', 'wf_cnd_not_has_handitem'),
    ('wf_cnd_not_triggerer_match', 'wf_cnd_not_triggerer_match'),
    ('wf_cnd_not_user_performs_action', 'wf_cnd_not_user_performs_action'),
    ('wf_cnd_team_has_rank', 'wf_cnd_team_has_rank'),
    ('wf_cnd_team_has_score', 'wf_cnd_team_has_score'),
    ('wf_cnd_triggerer_match', 'wf_cnd_triggerer_match'),
    ('wf_cnd_user_performs_action', 'wf_cnd_user_performs_action'),
    ('wf_trg_user_performs_action', 'wf_trg_user_performs_action'),
    ('wf_act_freeze', 'wf_act_freeze'),
    ('wf_act_furni_to_furni', 'wf_act_furni_to_furni'),
    ('wf_act_furni_to_user', 'wf_act_furni_to_user'),
    ('wf_act_rel_mov', 'wf_act_rel_mov'),
    ('wf_act_send_signal', 'wf_act_send_signal'),
    ('wf_act_neg_send_signal', 'wf_act_neg_send_signal'),
    ('wf_act_set_altitude', 'wf_act_set_altitude'),
    ('wf_act_unfreeze', 'wf_act_unfreeze'),
    ('wf_antenna1', 'antenna'),
    ('wf_antenna2', 'antenna'),
    ('wf_cnd_actor_dir', 'wf_cnd_actor_dir'),
    ('wf_cnd_has_altitude', 'wf_cnd_has_altitude'),
    ('wf_cnd_slc_quantity', 'wf_cnd_slc_quantity'),
    ('wf_numbertile1', 'default'),
    ('wf_numbertile2', 'default'),
    ('wf_slc_furni_altitude', 'wf_slc_furni_altitude'),
    ('wf_slc_furni_area', 'wf_slc_furni_area'),
    ('wf_slc_furni_bytype', 'wf_slc_furni_bytype'),
    ('wf_slc_furni_neighborhood', 'wf_slc_furni_neighborhood'),
    ('wf_slc_furni_onfurni', 'wf_slc_furni_onfurni'),
    ('wf_slc_furni_picks', 'wf_slc_furni_picks'),
    ('wf_slc_furni_signal', 'wf_slc_furni_signal'),
    ('wf_slc_users_area', 'wf_slc_users_area'),
    ('wf_slc_users_byaction', 'wf_slc_users_byaction'),
    ('wf_slc_users_byname', 'wf_slc_users_byname'),
    ('wf_slc_users_bytype', 'wf_slc_users_bytype'),
    ('wf_slc_users_group', 'wf_slc_users_group'),
    ('wf_slc_users_handitem', 'wf_slc_users_handitem'),
    ('wf_slc_users_neighborhood', 'wf_slc_users_neighborhood'),
    ('wf_slc_users_onfurni', 'wf_slc_users_onfurni'),
    ('wf_slc_users_signal', 'wf_slc_users_signal'),
    ('wf_slc_users_team', 'wf_slc_users_team'),
    ('wf_test_slc', 'default'),
    ('wf_tile1', 'default'),
    ('wf_tile2', 'default'),
    ('wf_trg_click_furni', 'wf_trg_click_furni'),
    ('wf_trg_period_short', 'wf_trg_period_short'),
    ('wf_trg_recv_signal', 'wf_trg_recv_signal'),
    ('wf_trg_stuff_state', 'wf_trg_stuff_state'),
    ('wf_xtra_anim_time', 'wf_xtra_anim_time'),
    ('wf_xtra_execution_limit', 'wf_xtra_execution_limit'),
    ('wf_xtra_filter_furni', 'wf_xtra_filter_furni'),
    ('wf_xtra_filter_users', 'wf_xtra_filter_users'),
    ('wf_xtra_mov_carry_users', 'wf_xtra_mov_carry_users'),
    ('wf_xtra_mov_no_animation', 'wf_xtra_mov_no_animation'),
    ('wf_xtra_mov_physics', 'wf_xtra_mov_physics'),
    ('wf_act_log', 'wf_act_log'),
    ('wf_act_neg_log', 'wf_act_neg_log'),
    ('wf_var_echo', 'wf_var_echo'),
    ('wf_xtra_var_lvlup_system', 'wf_xtra_var_lvlup_system'),
    ('wf_xtra_var_time_util', 'wf_xtra_var_time_util'),
    ('conf_invis_control', 'wf_conf_invis_control'),
    ('conf_handitem_block', 'wf_conf_handitem_block'),
    ('conf_wired_disable', 'wf_conf_wired_disable'),
    ('conf_queue_speed', 'wf_conf_queue_speed'),
    ('conf_area_hide', 'wf_conf_area_hide');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_interaction_repair_20260822` AS mapping
    ON mapping.`item_identifier` = item.`item_name`
SET item.`interaction_type` = mapping.`interaction_type`
WHERE item.`interaction_type` <> mapping.`interaction_type`;

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_interaction_repair_20260822` AS mapping
    ON mapping.`item_identifier` = item.`public_name`
LEFT JOIN `polaris_items_base_interaction_repair_20260822` AS item_name_mapping
    ON item_name_mapping.`item_identifier` = item.`item_name`
SET item.`interaction_type` = mapping.`interaction_type`
WHERE item_name_mapping.`item_identifier` IS NULL
  AND item.`interaction_type` <> mapping.`interaction_type`;

-- The companion historical update also marked invisible stack/walk helpers.
-- Match both naming columns so old and newer furnidata variants converge.
UPDATE `items_base`
SET `customparams` = 'is_invisible'
WHERE (
        `item_name` LIKE 'tile_stackmagic%'
        OR `public_name` LIKE 'tile_stackmagic%'
        OR `item_name` LIKE 'tile_walkmagic%'
        OR `public_name` LIKE 'tile_walkmagic%'
        OR `item_name` LIKE 'room_invisible_block%'
        OR `public_name` LIKE 'room_invisible_block%'
        OR `item_name` IN ('room_invisible_sit_tile', 'room_invisible_click_tile')
        OR `public_name` IN ('room_invisible_sit_tile', 'room_invisible_click_tile')
    )
  AND `customparams` <> 'is_invisible';

DROP TEMPORARY TABLE `polaris_items_base_interaction_repair_20260822`;
