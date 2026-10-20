package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialQuantifier;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Passes when the resolved user's duckets do NOT compare to {@code amount} as the operator says - the
 * opposite of {@link WiredConditionHabboHasDuckets} with the same settings. It keeps
 * {@link WiredConditionTeamGameBase}'s storage - the amount, the user source and the quantifier are the
 * settings it needs - but answers {@link WiredConditionType#USER_AMOUNT}, whose dialog leaves out the
 * team colour. A row saved before the operator was honoured reads as {@code >=}, so it still passes
 * below the amount - the one way this box used to compare.
 */
public class WiredConditionHabboLacksDuckets extends WiredConditionTeamGameBase implements WiredOfficialQuantifier {
    public static final WiredConditionType type = WiredConditionType.USER_AMOUNT;

    private static final int DUCKETS_CURRENCY = 0;

    private int teamType = GameTeamColors.RED.type;
    private int comparison = WiredComparison.GREATER_EQUAL;
    private int amount = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionHabboLacksDuckets(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboLacksDuckets(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        List<RoomUnit> users = this.resolveUsers(ctx, this.userSource);

        return this.matchesQuantifier(users, this.quantifier, roomUnit -> this.matchesUser(room, roomUnit));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.teamType, this.comparison, this.amount, this.userSource, this.quantifier));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSettings();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException ignored) {
            this.resetSettings();
            return;
        }
        if (data == null) {
            return;
        }

        this.teamType = this.normalizeExplicitTeamType(data.teamType);
        // Rows written before the operator was honoured carry no "operator" field. They keep the one
        // way this box always compared, whatever comparison the dialog echoed back into them.
        this.comparison =
                (data.operator == null) ? WiredComparison.GREATER_EQUAL : WiredComparison.normalize(data.operator);
        this.amount = this.normalizeScore(data.score);
        this.userSource = this.normalizeUserSource(data.userSource);
        this.quantifier = this.normalizeQuantifier(data.quantifier);
    }

    @Override
    public void onPickUp() {
        this.resetSettings();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.teamType);
        message.appendInt(this.comparison);
        message.appendInt(this.amount);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.resetSettings();

        if (params.length > 0) this.teamType = this.normalizeExplicitTeamType(params[0]);
        if (params.length > 1) this.comparison = WiredComparison.normalize(params[1]);
        if (params.length > 2) this.amount = this.normalizeScore(params[2]);
        if (params.length > 3) this.userSource = this.normalizeUserSource(params[3]);
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());

        return true;
    }

    private boolean matchesUser(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return false;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getHabboInfo() == null) {
            return false;
        }

        int value = habbo.getHabboInfo().getCurrencyAmount(DUCKETS_CURRENCY);

        return !WiredComparison.compare(value, this.amount, this.comparison);
    }

    private void resetSettings() {
        this.teamType = GameTeamColors.RED.type;
        this.comparison = WiredComparison.GREATER_EQUAL;
        this.amount = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    static class JsonData {
        int teamType;
        /** Null on rows written before the box honoured an operator. */
        Integer operator;

        int score;
        int userSource;
        int quantifier;

        public JsonData(int teamType, int operator, int score, int userSource, int quantifier) {
            this.teamType = teamType;
            this.operator = operator;
            this.score = score;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }
}
