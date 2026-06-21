package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredExtraVariableLevelUpSystem extends InteractionWiredExtra {
    public static final int CODE = 82;

    public static final int MODE_LINEAR = 1;
    public static final int MODE_EXPONENTIAL = 2;
    public static final int MODE_MANUAL = 3;

    public static final int SUB_CURRENT_LEVEL = 0;
    public static final int SUB_CURRENT_XP = 1;
    public static final int SUB_LEVEL_PROGRESS = 2;
    public static final int SUB_LEVEL_PROGRESS_PERCENT = 3;
    public static final int SUB_TOTAL_XP_REQUIRED = 4;
    public static final int SUB_XP_REMAINING = 5;
    public static final int SUB_IS_AT_MAX = 6;
    public static final int SUB_MAX_LEVEL = 7;
    public static final int SUBVARIABLE_COUNT = 8;

    private static final int DEFAULT_STEP_SIZE = 100;
    private static final int DEFAULT_MAX_LEVEL = 10;
    private static final int DEFAULT_FIRST_LEVEL_XP = 100;
    private static final int DEFAULT_INCREASE_FACTOR = 100;
    private static final int MAX_MANUAL_TEXT_LENGTH = 4096;

    private int mode = MODE_LINEAR;
    private int stepSize = DEFAULT_STEP_SIZE;
    private int maxLevel = DEFAULT_MAX_LEVEL;
    private int firstLevelXp = DEFAULT_FIRST_LEVEL_XP;
    private int increaseFactor = DEFAULT_INCREASE_FACTOR;
    private String interpolationText = "";
    private int subvariableMask = (1 << SUB_CURRENT_LEVEL) | (1 << SUB_CURRENT_XP);

    public WiredExtraVariableLevelUpSystem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableLevelUpSystem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.applyConfig(parseJsonData(settings.getStringParam()));
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.mode,
            this.stepSize,
            this.maxLevel,
            this.firstLevelXp,
            this.increaseFactor,
            this.interpolationText,
            this.getSelectedSubvariables()
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        this.applyConfig(parseJsonData(wiredData));
    }

    @Override
    public void onPickUp() {
        this.mode = MODE_LINEAR;
        this.stepSize = DEFAULT_STEP_SIZE;
        this.maxLevel = DEFAULT_MAX_LEVEL;
        this.firstLevelXp = DEFAULT_FIRST_LEVEL_XP;
        this.increaseFactor = DEFAULT_INCREASE_FACTOR;
        this.interpolationText = "";
        this.subvariableMask = (1 << SUB_CURRENT_LEVEL) | (1 << SUB_CURRENT_XP);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getMode() {
        return this.mode;
    }

    public int getStepSize() {
        return this.stepSize;
    }

    public int getMaxLevel() {
        return this.maxLevel;
    }

    public int getFirstLevelXp() {
        return this.firstLevelXp;
    }

    public int getIncreaseFactor() {
        return this.increaseFactor;
    }

    public String getInterpolationText() {
        return this.interpolationText;
    }

    public boolean hasSubvariable(int subvariableType) {
        return subvariableType >= 0
            && subvariableType < SUBVARIABLE_COUNT
            && ((this.subvariableMask & (1 << subvariableType)) != 0);
    }

    public List<Integer> getSelectedSubvariables() {
        List<Integer> result = new ArrayList<>();

        for (int index = 0; index < SUBVARIABLE_COUNT; index++) {
            if (this.hasSubvariable(index)) {
                result.add(index);
            }
        }

        return result;
    }

    private void applyConfig(JsonData data) {
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.mode = normalizeMode(data.mode);
        this.stepSize = normalizeNonNegative(data.stepSize, DEFAULT_STEP_SIZE);
        this.maxLevel = normalizeMaxLevel(data.maxLevel);
        this.firstLevelXp = normalizeNonNegative(data.firstLevelXp, DEFAULT_FIRST_LEVEL_XP);
        this.increaseFactor = normalizeNonNegative(data.increaseFactor, DEFAULT_INCREASE_FACTOR);
        this.interpolationText = normalizeInterpolationText(data.interpolationText);
        this.subvariableMask = normalizeSubvariableMask(data.subvariables);
    }

    private static JsonData parseJsonData(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new JsonData();
        }

        try {
            if (value.trim().startsWith("{")) {
                JsonData data = WiredExtraPayloadGuard.fromJson(value, JsonData.class);
                return (data != null) ? data : new JsonData();
            }
        } catch (Exception ignored) {
        }

        JsonData fallback = new JsonData();
        fallback.interpolationText = normalizeInterpolationText(value);
        fallback.mode = MODE_MANUAL;
        return fallback;
    }

    private static int normalizeMode(int value) {
        return switch (value) {
            case MODE_EXPONENTIAL, MODE_MANUAL -> value;
            default -> MODE_LINEAR;
        };
    }

    private static int normalizeNonNegative(int value, int fallback) {
        return Math.max(0, (value > 0) ? value : fallback);
    }

    private static int normalizeMaxLevel(int value) {
        return Math.max(1, (value > 0) ? value : DEFAULT_MAX_LEVEL);
    }

    private static String normalizeInterpolationText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace("\r", "");
        if (normalized.length() > MAX_MANUAL_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_MANUAL_TEXT_LENGTH);
        }

        return normalized;
    }

    private static int normalizeSubvariableMask(List<Integer> subvariables) {
        if (subvariables == null) {
            return (1 << SUB_CURRENT_LEVEL) | (1 << SUB_CURRENT_XP);
        }

        int mask = 0;
        for (Integer subvariable : subvariables) {
            if (subvariable == null || subvariable < 0 || subvariable >= SUBVARIABLE_COUNT) {
                continue;
            }

            mask |= (1 << subvariable);
        }

        return mask;
    }

    static class JsonData {
        int mode = MODE_LINEAR;
        int stepSize = DEFAULT_STEP_SIZE;
        int maxLevel = DEFAULT_MAX_LEVEL;
        int firstLevelXp = DEFAULT_FIRST_LEVEL_XP;
        int increaseFactor = DEFAULT_INCREASE_FACTOR;
        String interpolationText = "";
        List<Integer> subvariables = null;

        JsonData() {
        }

        JsonData(int mode, int stepSize, int maxLevel, int firstLevelXp, int increaseFactor, String interpolationText, List<Integer> subvariables) {
            this.mode = mode;
            this.stepSize = stepSize;
            this.maxLevel = maxLevel;
            this.firstLevelXp = firstLevelXp;
            this.increaseFactor = increaseFactor;
            this.interpolationText = interpolationText;
            this.subvariables = subvariables;
        }
    }
}
