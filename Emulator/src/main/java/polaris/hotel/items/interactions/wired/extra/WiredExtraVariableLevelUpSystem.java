package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.WiredCompatibilityDiagnostics;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;

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

    private static final int OFFICIAL_MODE_MANUAL = 0;
    private static final int OFFICIAL_MODE_LINEAR = 1;
    private static final int OFFICIAL_MODE_EXPONENTIAL = 2;
    private static final int DEFAULT_STEP_SIZE = 100;
    // Official defaults for a new box: 50 levels, 20 % growth, every sub-variable.
    private static final int DEFAULT_MAX_LEVEL = 50;
    // Hard ceiling on the level count. Consumers rebuild the full threshold
    // table on every variable read (WiredVariableLevelSystemSupport), so an
    // unbounded maxLevel or manual anchor level is a room-thread DoS.
    // Kept private (not public) so it stays off the frozen wired plugin ABI
    // (WiredPublicSurfaceCompatibilityTest); WiredVariableLevelSystemSupport
    // holds its own private copy of the same cap.
    private static final int MAX_LEVEL = 10_000;
    private static final int DEFAULT_FIRST_LEVEL_XP = 100;
    private static final int DEFAULT_INCREASE_FACTOR = 20;
    private static final int DEFAULT_SUBVARIABLE_MASK = (1 << SUBVARIABLE_COUNT) - 1;
    private static final int MAX_MANUAL_TEXT_LENGTH = 4096;

    private int mode = MODE_LINEAR;
    private int stepSize = DEFAULT_STEP_SIZE;
    private int maxLevel = DEFAULT_MAX_LEVEL;
    private int firstLevelXp = DEFAULT_FIRST_LEVEL_XP;
    private int increaseFactor = DEFAULT_INCREASE_FACTOR;
    private String interpolationText = "";
    private int subvariableMask = DEFAULT_SUBVARIABLE_MASK;

    public WiredExtraVariableLevelUpSystem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableLevelUpSystem(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        String text = settings.getStringParam();
        if (params.length == 0 || (text != null && text.trim().startsWith("{"))) {
            // A save from before the official layout: the whole config as JSON text.
            this.applyConfig(parseJsonData(text));
            return true;
        }

        // Official layout: [sub-variable mask, mode (0 manual, 1 linear, 2 exponential), the mode's
        // figures: linear step and max level, or exponential first level XP, growth % and max level];
        // the manual "level=xp" lines as text.
        this.subvariableMask = params[0] & ((1 << SUBVARIABLE_COUNT) - 1);
        int officialMode = params.length > 1 ? params[1] : OFFICIAL_MODE_LINEAR;
        switch (officialMode) {
            case OFFICIAL_MODE_MANUAL -> {
                this.mode = MODE_MANUAL;
                this.interpolationText = normalizeInterpolationText(text);
            }
            case OFFICIAL_MODE_EXPONENTIAL -> {
                this.mode = MODE_EXPONENTIAL;
                this.firstLevelXp =
                        normalizeNonNegative(param(params, 2, DEFAULT_FIRST_LEVEL_XP), DEFAULT_FIRST_LEVEL_XP);
                this.increaseFactor = Math.max(0, param(params, 3, DEFAULT_INCREASE_FACTOR));
                this.maxLevel = normalizeMaxLevel(param(params, 4, DEFAULT_MAX_LEVEL));
            }
            default -> {
                this.mode = MODE_LINEAR;
                this.stepSize = normalizeNonNegative(param(params, 2, DEFAULT_STEP_SIZE), DEFAULT_STEP_SIZE);
                this.maxLevel = normalizeMaxLevel(param(params, 3, DEFAULT_MAX_LEVEL));
            }
        }
        return true;
    }

    private static int param(int[] params, int index, int fallback) {
        return index < params.length ? params[index] : fallback;
    }

    /** The official int params: the mask, the mode and that mode's figures. */
    int[] officialIntParams() {
        return switch (this.mode) {
            case MODE_MANUAL -> new int[] {this.subvariableMask, OFFICIAL_MODE_MANUAL};
            case MODE_EXPONENTIAL ->
                new int[] {
                    this.subvariableMask,
                    OFFICIAL_MODE_EXPONENTIAL,
                    this.firstLevelXp,
                    this.increaseFactor,
                    this.maxLevel
                };
            default -> new int[] {this.subvariableMask, OFFICIAL_MODE_LINEAR, this.stepSize, this.maxLevel};
        };
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.mode,
                        this.stepSize,
                        this.maxLevel,
                        this.firstLevelXp,
                        this.increaseFactor,
                        this.interpolationText,
                        this.getSelectedSubvariables()));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.mode == MODE_MANUAL ? this.interpolationText : "");
        int[] params = this.officialIntParams();
        message.appendInt(params.length);
        for (int param : params) message.appendInt(param);
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
        this.subvariableMask = DEFAULT_SUBVARIABLE_MASK;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

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
        // 0 % growth is a valid (flat) curve.
        this.increaseFactor = Math.max(0, data.increaseFactor);
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
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EXTRA_LEVEL_UP_JSON, ignored);
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
        return Math.min(MAX_LEVEL, Math.max(1, (value > 0) ? value : DEFAULT_MAX_LEVEL));
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
            // A box saved before sub-variables were stored keeps the two it had.
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

        JsonData() {}

        JsonData(
                int mode,
                int stepSize,
                int maxLevel,
                int firstLevelXp,
                int increaseFactor,
                String interpolationText,
                List<Integer> subvariables) {
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
