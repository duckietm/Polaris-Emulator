-- =====================================================================
-- 014_conf_control_interaction_type_fix.sql
-- =====================================================================
-- The "conf_*" CONTROL furni (room-wired toggles: invisible-furni control,
-- wired disabler, area hide, queue/roller speed, handitem block) have a
-- working, REGISTERED interaction class in ItemManager.loadItemInteractions()
-- (InteractionConfInvisControl / InteractionWiredDisableControl /
-- InteractionAreaHideControl / InteractionQueueSpeedControl /
-- InteractionHanditemBlockControl), but their items_base rows carry a WRONG
-- interaction_type, so they load as InteractionDefault (inert) — e.g.:
--   conf_invis_control   -> 'default'                       (should be conf_invis_control)
--   conf_wired_disable   -> 'wf_act_disable_click_through'  (should be wf_conf_wired_disable)
--   conf_area_hide       -> 'default'                       (should be conf_area_hide)
--   conf_queue_speed     -> 'default'                       (should be wf_conf_queue_speed)
--   conf_handitem_block  -> 'conf_handitem_block'(unregd)   (should be wf_conf_handitem_block)
--
-- Fix: set interaction_type to the REGISTERED name for each, scoped by item_name
-- and only when it currently differs (idempotent — re-running matches nothing).
-- Requires an emulator restart (items_base is read at startup).
-- =====================================================================

UPDATE items_base SET interaction_type = 'conf_invis_control'
    WHERE item_name = 'conf_invis_control'   AND interaction_type <> 'conf_invis_control';

UPDATE items_base SET interaction_type = 'wf_conf_wired_disable'
    WHERE item_name = 'conf_wired_disable'   AND interaction_type <> 'wf_conf_wired_disable';

UPDATE items_base SET interaction_type = 'conf_area_hide'
    WHERE item_name = 'conf_area_hide'       AND interaction_type <> 'conf_area_hide';

UPDATE items_base SET interaction_type = 'wf_conf_queue_speed'
    WHERE item_name = 'conf_queue_speed'     AND interaction_type <> 'wf_conf_queue_speed';

UPDATE items_base SET interaction_type = 'wf_conf_handitem_block'
    WHERE item_name = 'conf_handitem_block'  AND interaction_type <> 'wf_conf_handitem_block';
