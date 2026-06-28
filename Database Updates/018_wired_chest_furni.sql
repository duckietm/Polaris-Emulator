-- =====================================================================
-- 018_wired_chest_furni.sql
-- =====================================================================
-- Phase-2 chest/storage, furni slice. Point the items_base rows at their
-- newly-registered classes. Same pattern as 013-017. Emulator restart req.
-- Idempotent.
--
--   wf_storage_furni1/2/_starter -> InteractionWiredChestFurni  (Furni Chest, dialog code 101)
--   wf_act_give_furni            -> WiredEffectGiveFurniFromChest (effect code 102)
--   wf_cnd_chest_has_item_type   -> WiredConditionChestHasItemType (condition code 48)
--
-- NOTE: wf_storage_furni2 may already read 'wf_storage_furni2' (legacy) — the
-- guard makes this a no-op there. wf_xtra_scan_chest_furni_by_type (scanner)
-- is NOT in this slice (future).
-- =====================================================================

UPDATE items_base SET interaction_type = 'wf_storage_furni1'
    WHERE item_name = 'wf_storage_furni1'        AND interaction_type <> 'wf_storage_furni1';

UPDATE items_base SET interaction_type = 'wf_storage_furni2'
    WHERE item_name = 'wf_storage_furni2'        AND interaction_type <> 'wf_storage_furni2';

UPDATE items_base SET interaction_type = 'wf_storage_furni_starter'
    WHERE item_name = 'wf_storage_furni_starter' AND interaction_type <> 'wf_storage_furni_starter';

UPDATE items_base SET interaction_type = 'wf_act_give_furni'
    WHERE item_name = 'wf_act_give_furni'        AND interaction_type <> 'wf_act_give_furni';

UPDATE items_base SET interaction_type = 'wf_cnd_chest_has_item_type'
    WHERE item_name = 'wf_cnd_chest_has_item_type' AND interaction_type <> 'wf_cnd_chest_has_item_type';
