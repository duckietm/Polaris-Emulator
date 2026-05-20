-- ============================================================
-- Fix: acc_supporttool wrongly granted to VIP / wrongly denied to Super Mod
-- ============================================================
-- The default permission_definitions seed shipped acc_supporttool
-- with rank pattern (0, 1, 1, 1, 1, 0, 1) — i.e. rank_2 (VIP) and
-- rank_3 (X, junior helper) had ALLOWED, while rank_6 (Super Mod)
-- did NOT. That's two bugs:
--
--   * VIP users see the ModTools button on the toolbar and can
--     open Room/User info windows. The actual sanction endpoints
--     still gate on ACC_SUPPORTTOOL server-side so they can't
--     actually moderate, but the UI exposure is wrong and lets a
--     VIP request user info / room info / chatlogs they have no
--     business reading.
--   * Super Mod is denied the tool entirely, which is obviously
--     unintended given the rank name.
--
-- Intended pattern: only Support (4) and up — (0, 0, 0, 1, 1, 1, 1).
--
-- Run on existing deployments to align with the corrected default
-- seed in `Default Database/FullDatabase.sql`. Idempotent.

UPDATE `permission_definitions`
   SET `rank_1` = 0,
       `rank_2` = 0,
       `rank_3` = 0,
       `rank_4` = 1,
       `rank_5` = 1,
       `rank_6` = 1,
       `rank_7` = 1
 WHERE `permission_key` = 'acc_supporttool';
