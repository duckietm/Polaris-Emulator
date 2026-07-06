-- =====================================================================
-- 019_wired_transactions_scanner.sql
-- =====================================================================
-- Phase-2: chest scanner add-on + transaction effects/triggers (v1).
-- Point the inert items_base rows at their new classes. Pattern 013-018.
-- Emulator restart required. Idempotent.
--
--   wf_xtra_scan_chest_furni_by_type -> WiredEffectScanChestFurniByType  (selector, code 103)
--   wf_act_init_transaction          -> WiredEffectInitTransaction       (effect 104 -> fires complete)
--   wf_act_cancel_transaction        -> WiredEffectCancelTransaction     (effect 105 -> fires fail)
--   wf_trg_transaction_complete      -> WiredTriggerTransactionComplete  (trigger 27)
--   wf_trg_transaction_fail          -> WiredTriggerTransactionFail      (trigger 28)
-- =====================================================================

UPDATE items_base SET interaction_type = 'wf_xtra_scan_chest_furni_by_type'
    WHERE item_name = 'wf_xtra_scan_chest_furni_by_type' AND interaction_type <> 'wf_xtra_scan_chest_furni_by_type';

UPDATE items_base SET interaction_type = 'wf_act_init_transaction'
    WHERE item_name = 'wf_act_init_transaction'   AND interaction_type <> 'wf_act_init_transaction';

UPDATE items_base SET interaction_type = 'wf_act_cancel_transaction'
    WHERE item_name = 'wf_act_cancel_transaction' AND interaction_type <> 'wf_act_cancel_transaction';

UPDATE items_base SET interaction_type = 'wf_trg_transaction_complete'
    WHERE item_name = 'wf_trg_transaction_complete' AND interaction_type <> 'wf_trg_transaction_complete';

UPDATE items_base SET interaction_type = 'wf_trg_transaction_fail'
    WHERE item_name = 'wf_trg_transaction_fail'   AND interaction_type <> 'wf_trg_transaction_fail';
