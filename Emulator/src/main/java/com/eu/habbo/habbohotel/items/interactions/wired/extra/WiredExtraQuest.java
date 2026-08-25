package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Quest box ({@code wf_var_quest}, dialog code 108). Exposes derived read-only sub-variables of its
 * co-located base counter against a configured target:
 * {@code progress, target, is_complete, percent, remaining}.
 */
public class WiredExtraQuest extends WiredExtraQuestBase {
    public static final int CODE = 108;

    public static final int SUB_PROGRESS = 0;
    public static final int SUB_TARGET = 1;
    public static final int SUB_IS_COMPLETE = 2;
    public static final int SUB_PERCENT = 3;
    public static final int SUB_REMAINING = 4;
    public static final int SUBVARIABLE_COUNT = 5;

    public WiredExtraQuest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraQuest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int code() {
        return CODE;
    }

    @Override
    public int subvariableCount() {
        return SUBVARIABLE_COUNT;
    }

    @Override
    public String subvariableKey(int subType) {
        return switch (subType) {
            case SUB_PROGRESS -> "progress";
            case SUB_TARGET -> "target";
            case SUB_IS_COMPLETE -> "is_complete";
            case SUB_PERCENT -> "percent";
            case SUB_REMAINING -> "remaining";
            default -> "value";
        };
    }

    @Override
    public Integer derive(int subType, Integer baseValue) {
        if (baseValue == null) return null;

        int progress = Math.max(0, baseValue);
        int target = Math.max(0, this.targetValue);

        return switch (subType) {
            case SUB_PROGRESS -> progress;
            case SUB_TARGET -> target;
            case SUB_IS_COMPLETE -> (target > 0 && progress >= target) ? 1 : 0;
            case SUB_PERCENT -> (target <= 0) ? 100 : Math.max(0, Math.min(100, (int) Math.floor((progress * 100D) / target)));
            case SUB_REMAINING -> Math.max(0, target - progress);
            default -> null;
        };
    }
}
