package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredQuantifier;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialQuantifier;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.users.UserProfileLevel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Passes when a user's account level compares as asked: the level the extended profile shows (from the achievement
 * score), the same value as the {@code @level} variable, as in Habbo. Bots and pets have no account level and fail.
 *
 * <p>The comparison is AIR's LevelMatches radio: 0 lower than, 1 equal to, 2 higher than. Boxes saved before kept
 * a six-way comparison ({@code COMPARISON_*}); they load converted to the same test ({@code >= n} as {@code > n-1},
 * {@code <= n} as {@code < n+1}), and a saved "not equal", which AIR cannot show, keeps its test until the box is
 * saved again.
 *
 * <p>Older saves also stored a variable; it is kept so they load and re-save unchanged, but no longer read.
 */
public class WiredConditionUserLevel extends InteractionWiredCondition implements WiredOfficialQuantifier {
    public static final WiredConditionType type = WiredConditionType.USER_LEVEL;

    /** AIR LevelMatches comparisons ({@code wiredfurni.params.comparison.0..2}). */
    public static final int OFFICIAL_LESS_THAN = 0;

    public static final int OFFICIAL_EQUAL = 1;
    public static final int OFFICIAL_GREATER_THAN = 2;

    /** The six-way comparison boxes stored before the official radio; read only to convert them. */
    public static final int COMPARISON_GREATER_THAN = 0;

    public static final int COMPARISON_GREATER_THAN_OR_EQUAL = 1;
    public static final int COMPARISON_EQUAL = 2;
    public static final int COMPARISON_LESS_THAN_OR_EQUAL = 3;
    public static final int COMPARISON_LESS_THAN = 4;
    public static final int COMPARISON_NOT_EQUAL = 5;

    public static final int QUANTIFIER_ALL = 0;
    public static final int QUANTIFIER_ANY = 1;

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10_000;

    /** Stored data format: 2 holds the official comparison; absent, the six-way one. */
    private static final int FORMAT_OFFICIAL = 2;

    private int level = MIN_LEVEL;
    private int comparison = OFFICIAL_EQUAL;
    /** A loaded six-way "not equal" box, tested as such until it is saved with an official comparison. */
    private boolean legacyNotEqual = false;

    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;
    private String variableToken = "";
    private int variableItemId = 0;

    public WiredConditionUserLevel(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserLevel(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx == null ? null : ctx.room();
        if (room == null) {
            return false;
        }

        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) {
            return WiredQuantifier.overEmpty(this.quantifier == QUANTIFIER_ALL);
        }

        boolean anyMatched = false;
        for (RoomUnit target : targets) {
            Integer reached = this.levelOf(room, target);
            boolean matched = reached != null && this.matches(reached);

            if (this.quantifier == QUANTIFIER_ALL) {
                // A user with no level at all fails "all", because the question asked is whether
                // everyone has reached a level, and someone who has never earned any has not.
                if (!matched) {
                    return false;
                }
                anyMatched = true;
            } else if (matched) {
                return true;
            }
        }

        return this.quantifier == QUANTIFIER_ALL && anyMatched;
    }

    private static Integer levelOf(Room room, RoomUnit target) {
        Habbo habbo = room.getHabbo(target);
        if (habbo == null || habbo.getHabboStats() == null) {
            return null;
        }
        return UserProfileLevel.accountLevel(habbo.getHabboStats().getAchievementScore());
    }

    boolean matches(int reached) {
        if (this.legacyNotEqual) {
            return reached != this.level;
        }
        return switch (this.comparison) {
            case OFFICIAL_LESS_THAN -> reached < this.level;
            case OFFICIAL_GREATER_THAN -> reached > this.level;
            default -> reached == this.level;
        };
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        this.level = normalizeLevel(param(intParams, 0, MIN_LEVEL));
        this.comparison = normalizeComparison(param(intParams, 1, OFFICIAL_EQUAL));
        this.legacyNotEqual = false;
        // Official layout: [level, comparison (0 <, 1 =, 2 >)], the users in the official source slot;
        // the quantifier (0 all, 1 any) in its slot.
        this.userSource = settings.getUserSourceTypes().length > 0
                ? settings.getUserSourceTypes()[0]
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = settings.getQuantifierCode() == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.variableToken = normalizeToken(settings.getStringParam());
        this.variableItemId = tokenItemId(this.variableToken);

        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.level,
                        // A kept "not equal" stays in the six-way form it was loaded from.
                        this.legacyNotEqual ? COMPARISON_NOT_EQUAL : this.comparison,
                        this.userSource,
                        this.quantifier,
                        this.variableToken,
                        this.variableItemId,
                        this.legacyNotEqual ? null : FORMAT_OFFICIAL));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken);
        message.appendInt(2);
        message.appendInt(this.level);
        message.appendInt(this.comparison);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setExtradata("");

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        if (data.format != null && data.format == FORMAT_OFFICIAL) {
            this.level = normalizeLevel(data.level);
            this.comparison = normalizeComparison(data.comparison);
            this.legacyNotEqual = false;
        } else {
            this.applyLegacyComparison(data.level, data.comparison);
        }
        this.userSource = data.userSource;
        this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.variableToken = normalizeToken(data.variableToken);
        this.variableItemId = data.variableItemId > 0 ? data.variableItemId : tokenItemId(this.variableToken);
    }

    /**
     * A six-way comparison as the official one with the same test on whole levels: {@code >= n} is {@code > n-1} and
     * {@code <= n} is {@code < n+1}. "Not equal" has no official form and keeps its own test.
     */
    void applyLegacyComparison(int storedLevel, int storedComparison) {
        int legacyLevel = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, storedLevel));
        this.legacyNotEqual = false;
        switch (storedComparison) {
            case COMPARISON_GREATER_THAN -> {
                this.level = legacyLevel;
                this.comparison = OFFICIAL_GREATER_THAN;
            }
            case COMPARISON_EQUAL -> {
                this.level = legacyLevel;
                this.comparison = OFFICIAL_EQUAL;
            }
            case COMPARISON_LESS_THAN_OR_EQUAL -> {
                this.level = legacyLevel + 1;
                this.comparison = OFFICIAL_LESS_THAN;
            }
            case COMPARISON_LESS_THAN -> {
                this.level = legacyLevel;
                this.comparison = OFFICIAL_LESS_THAN;
            }
            case COMPARISON_NOT_EQUAL -> {
                this.level = legacyLevel;
                this.comparison = OFFICIAL_EQUAL;
                this.legacyNotEqual = true;
            }
            default -> {
                // ">=" (also what an unknown code meant).
                this.level = legacyLevel - 1;
                this.comparison = OFFICIAL_GREATER_THAN;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.level = MIN_LEVEL;
        this.comparison = OFFICIAL_EQUAL;
        this.legacyNotEqual = false;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
        this.variableToken = "";
        this.variableItemId = 0;
    }

    static int normalizeLevel(int value) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, value));
    }

    static int normalizeComparison(int value) {
        return value >= OFFICIAL_LESS_THAN && value <= OFFICIAL_GREATER_THAN ? value : OFFICIAL_EQUAL;
    }

    int getLevel() {
        return this.level;
    }

    int getComparison() {
        return this.comparison;
    }

    static String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    static int tokenItemId(String token) {
        String digits = normalizeToken(token);
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }

    static class JsonData {
        int level;
        int comparison;
        int userSource;
        int quantifier;
        String variableToken;
        int variableItemId;
        Integer format;

        JsonData(
                int level,
                int comparison,
                int userSource,
                int quantifier,
                String variableToken,
                int variableItemId,
                Integer format) {
            this.level = level;
            this.comparison = comparison;
            this.userSource = userSource;
            this.quantifier = quantifier;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.format = format;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }
}
