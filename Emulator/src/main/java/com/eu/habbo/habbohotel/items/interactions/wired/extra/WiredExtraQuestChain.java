package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Quest Chain box ({@code wf_var_quest_chain}, dialog code 109). Sequences a multi-step quest: its
 * co-located base counter is the "current step" (designers increment it as each sub-quest completes),
 * and {@code targetValue} is the total number of steps. Exposes derived read-only sub-variables:
 * {@code current_step, total_steps, is_complete, percent}.
 *
 * <p>v1 uses the manual-advance model (a sub-quest's {@code is_complete} drives a change-var-val that
 * bumps the step counter), which fits the derived-variable pattern cleanly; automatic advance by reading
 * member quests is a future enhancement.</p>
 */
public class WiredExtraQuestChain extends WiredExtraQuestBase {
    public static final int CODE = 109;

    public static final int SUB_CURRENT_STEP = 0;
    public static final int SUB_TOTAL_STEPS = 1;
    public static final int SUB_IS_COMPLETE = 2;
    public static final int SUB_PERCENT = 3;
    public static final int SUBVARIABLE_COUNT = 4;

    public WiredExtraQuestChain(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraQuestChain(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            case SUB_CURRENT_STEP -> "current_step";
            case SUB_TOTAL_STEPS -> "total_steps";
            case SUB_IS_COMPLETE -> "is_complete";
            case SUB_PERCENT -> "percent";
            default -> "value";
        };
    }

    @Override
    public Integer derive(int subType, Integer baseValue) {
        if (baseValue == null) return null;

        int step = Math.max(0, baseValue);
        int total = Math.max(0, this.targetValue);

        return switch (subType) {
            case SUB_CURRENT_STEP -> (total > 0) ? Math.min(step, total) : step;
            case SUB_TOTAL_STEPS -> total;
            case SUB_IS_COMPLETE -> (total > 0 && step >= total) ? 1 : 0;
            case SUB_PERCENT -> (total <= 0) ? 100 : Math.max(0, Math.min(100, (int) Math.floor((step * 100D) / total)));
            default -> null;
        };
    }
}
