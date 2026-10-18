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
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.users.UserProfileLevel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Passes when a user's account level compares as asked: the level the extended profile shows (from the achievement
 * score), the same value as the {@code @level} variable, as in Habbo. Bots and pets have no account level and fail.
 *
 * <p>Older saves also stored a variable; it is kept so they load and re-save unchanged, but no longer read.
 */
public class WiredConditionUserLevel extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.USER_LEVEL;

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

    private int level = MIN_LEVEL;
    private int comparison = COMPARISON_GREATER_THAN_OR_EQUAL;
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
            return false;
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
        return switch (this.comparison) {
            case COMPARISON_GREATER_THAN -> reached > this.level;
            case COMPARISON_EQUAL -> reached == this.level;
            case COMPARISON_LESS_THAN_OR_EQUAL -> reached <= this.level;
            case COMPARISON_LESS_THAN -> reached < this.level;
            case COMPARISON_NOT_EQUAL -> reached != this.level;
            default -> reached >= this.level;
        };
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        this.level = normalizeLevel(param(intParams, 0, MIN_LEVEL));
        this.comparison = normalizeComparison(param(intParams, 1, COMPARISON_GREATER_THAN_OR_EQUAL));
        this.userSource = param(intParams, 2, WiredSourceUtil.SOURCE_TRIGGER);
        this.quantifier = param(intParams, 3, QUANTIFIER_ALL) == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
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
                        this.comparison,
                        this.userSource,
                        this.quantifier,
                        this.variableToken,
                        this.variableItemId));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken);
        message.appendInt(4);
        message.appendInt(this.level);
        message.appendInt(this.comparison);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
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

        this.level = normalizeLevel(data.level);
        this.comparison = normalizeComparison(data.comparison);
        this.userSource = data.userSource;
        this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.variableToken = normalizeToken(data.variableToken);
        this.variableItemId = data.variableItemId > 0 ? data.variableItemId : tokenItemId(this.variableToken);
    }

    @Override
    public void onPickUp() {
        this.level = MIN_LEVEL;
        this.comparison = COMPARISON_GREATER_THAN_OR_EQUAL;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
        this.variableToken = "";
        this.variableItemId = 0;
    }

    static int normalizeLevel(int value) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, value));
    }

    static int normalizeComparison(int value) {
        return value >= COMPARISON_GREATER_THAN && value <= COMPARISON_NOT_EQUAL
                ? value
                : COMPARISON_GREATER_THAN_OR_EQUAL;
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

        JsonData(int level, int comparison, int userSource, int quantifier, String variableToken, int variableItemId) {
            this.level = level;
            this.comparison = comparison;
            this.userSource = userSource;
            this.quantifier = quantifier;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
        }
    }
}
