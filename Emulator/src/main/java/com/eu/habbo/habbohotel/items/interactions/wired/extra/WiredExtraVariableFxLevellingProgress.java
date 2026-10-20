package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxStatus;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxStyles;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * A level badge with a progress bar for the experience variable on this tile. The client does not
 * work levels out: every status tells it the level, the level cap and whether the cap is reached,
 * and carries the experience at which the current level and the next one start as its range.
 * Those come from the level-up addon on the same tile ({@code wf_xtra_var_lvlup_system}); without
 * one the variable is shown as level one, 0 to 100. The category extra is the bar the badge style
 * draws beside it. Registered on {@code wf_xtra_var_fx_level}.
 */
public class WiredExtraVariableFxLevellingProgress extends WiredExtraVariableFx {
    public static final int CODE = 132;

    private static final int[] BADGE_BARS = {
        WiredVariableFxStyles.RENDERER_BLOCK,
        WiredVariableFxStyles.RENDERER_STRIPED,
        WiredVariableFxStyles.RENDERER_ARROW
    };

    public WiredExtraVariableFxLevellingProgress(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxLevellingProgress(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public int getCategory() {
        return WiredVariableFxStyles.CATEGORY_LEVELLING_PROGRESS;
    }

    @Override
    public int getCode() {
        return CODE;
    }

    @Override
    protected boolean usesValueRange() {
        return false;
    }

    @Override
    protected void addCategoryExtra(Map<String, String> extra, WiredVariableFxStyles.Style style, int rendererId) {
        extra.put(WiredVariableFxStyles.EXTRA_SUB_RENDERER, Integer.toString(this.getSegmentRendererId(rendererId)));
    }

    @Override
    protected int getSegmentRendererId(int rendererId) {
        if (rendererId != WiredVariableFxStyles.RENDERER_LEVEL_WITH_PROGRESS)
            return WiredVariableFxStyles.RENDERER_CLASSIC_MINI;

        int chosen = this.getCategoryExtra();
        for (int bar : BADGE_BARS) {
            if (bar == chosen) return chosen;
        }
        return WiredVariableFxStyles.RENDERER_BLOCK;
    }

    @Override
    public WiredVariableFxStatus resolveStatus(
            Room room,
            InteractionWiredExtra variableBox,
            WiredVariableFxStatus.Key key,
            int holderId,
            long value,
            String teamColor) {
        Map<String, String> extra = new LinkedHashMap<>();
        this.addColorExtra(extra, teamColor);

        // Without a level-up addon on the tile: level one of one, and no range of its own.
        WiredExtraVariableLevelUpSystem levelSystem = WiredVariableLevelSystemSupport.getLevelSystem(room, variableBox);
        Integer level = derive(levelSystem, WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL, value);
        Integer maxLevel = derive(levelSystem, WiredExtraVariableLevelUpSystem.SUB_MAX_LEVEL, value);
        Integer atMax = derive(levelSystem, WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX, value);
        Integer progress = derive(levelSystem, WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS, value);
        Integer remaining = derive(levelSystem, WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING, value);

        addLevelExtra(
                extra, (level != null) ? level : 1, (maxLevel != null) ? maxLevel : 1, atMax != null && atMax == 1);

        Long levelStart = null;
        Long nextStart = null;
        if (progress != null && remaining != null) {
            levelStart = value - progress;
            // At the cap both ends meet: with "is maxed" the client draws the bar full instead of empty.
            nextStart = (atMax != null && atMax == 1) ? levelStart : value + remaining;
        }

        return new WiredVariableFxStatus(key, false, value, levelStart, nextStart, extra);
    }

    /** A level badge has no range overrides, so the variable ids it would read do not matter. */
    @Override
    public WiredVariableFxStatus resolveStatus(
            Room room,
            InteractionWiredExtra variableBox,
            WiredVariableFxStatus.Key key,
            int holderId,
            long value,
            String teamColor,
            IntUnaryOperator variableIds) {
        return this.resolveStatus(room, variableBox, key, holderId, value, teamColor);
    }

    private static Integer derive(WiredExtraVariableLevelUpSystem levelSystem, int subvariable, long value) {
        if (levelSystem == null) return null;
        return WiredVariableLevelSystemSupport.getDerivedValue(
                levelSystem, subvariable, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)));
    }
}
