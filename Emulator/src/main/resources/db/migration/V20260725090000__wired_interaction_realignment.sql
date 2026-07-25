-- Re-points superseded wired furniture at its current interaction and translates
-- persisted box settings, so hotels that already imported the furniture keep working
-- without any manual re-configuration.
--
-- wf_xtra_mov_animation -> wf_xtra_mov_curve      (same seven styles + intensity)
-- wf_act_furni_opacity  -> wf_act_change_opacity  (adds duration + user source)
--
-- Data-only: no schema change. Every statement is scoped by the value it replaces,
-- so re-running is a no-op.

UPDATE items_base
SET interaction_type = 'wf_xtra_mov_curve'
WHERE interaction_type = 'wf_xtra_mov_animation';

UPDATE items_base
SET interaction_type = 'wf_act_change_opacity'
WHERE interaction_type = 'wf_act_furni_opacity';

-- Placed movement boxes: {animationEffect, gravityIntensity} -> {curveType, intensity}.
-- The style ordering is identical (0 linear .. 6 drop), so values carry over as-is.
UPDATE items i
    JOIN items_base b ON b.id = i.item_id
SET i.wired_data = JSON_OBJECT(
        'curveType', CAST(JSON_UNQUOTE(JSON_EXTRACT(i.wired_data, '$.animationEffect')) AS UNSIGNED),
        'intensity', CAST(JSON_UNQUOTE(JSON_EXTRACT(i.wired_data, '$.gravityIntensity')) AS UNSIGNED))
WHERE b.interaction_type = 'wf_xtra_mov_curve'
  AND JSON_VALID(i.wired_data)
  AND JSON_EXTRACT(i.wired_data, '$.animationEffect') IS NOT NULL;

UPDATE room_templates_items i
    JOIN items_base b ON b.id = i.item_id
SET i.wired_data = JSON_OBJECT(
        'curveType', CAST(JSON_UNQUOTE(JSON_EXTRACT(i.wired_data, '$.animationEffect')) AS UNSIGNED),
        'intensity', CAST(JSON_UNQUOTE(JSON_EXTRACT(i.wired_data, '$.gravityIntensity')) AS UNSIGNED))
WHERE b.interaction_type = 'wf_xtra_mov_curve'
  AND JSON_VALID(i.wired_data)
  AND JSON_EXTRACT(i.wired_data, '$.animationEffect') IS NOT NULL;

-- Placed opacity boxes keep opacity/easing/clickThrough/furniSource/delay/itemIds as-is
-- (same field names), but the visibility flag is inverted between the two layouts:
-- previously 0 = everyone / 1 = triggering user, now 0 = source users / 1 = everyone.
-- Writing durationSeconds and userSource records the current layout, so the rows are
-- no longer matched on a re-run.
UPDATE items i
    JOIN items_base b ON b.id = i.item_id
SET i.wired_data = JSON_SET(
        i.wired_data,
        '$.visibility',
        1 - CAST(JSON_UNQUOTE(JSON_EXTRACT(i.wired_data, '$.visibility')) AS UNSIGNED),
        '$.durationSeconds',
        0,
        '$.userSource',
        0)
WHERE b.interaction_type = 'wf_act_change_opacity'
  AND JSON_VALID(i.wired_data)
  AND JSON_EXTRACT(i.wired_data, '$.visibility') IS NOT NULL
  AND JSON_EXTRACT(i.wired_data, '$.durationSeconds') IS NULL;

UPDATE room_templates_items i
    JOIN items_base b ON b.id = i.item_id
SET i.wired_data = JSON_SET(
        i.wired_data,
        '$.visibility',
        1 - CAST(JSON_UNQUOTE(JSON_EXTRACT(i.wired_data, '$.visibility')) AS UNSIGNED),
        '$.durationSeconds',
        0,
        '$.userSource',
        0)
WHERE b.interaction_type = 'wf_act_change_opacity'
  AND JSON_VALID(i.wired_data)
  AND JSON_EXTRACT(i.wired_data, '$.visibility') IS NOT NULL
  AND JSON_EXTRACT(i.wired_data, '$.durationSeconds') IS NULL;
