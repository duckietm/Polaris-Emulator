package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionTeamMember extends InteractionWiredCondition {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.ACTOR_IN_TEAM;

    private GameTeamColors teamColor = GameTeamColors.RED;
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ALL;

    public WiredConditionTeamMember(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTeamMember(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            return this.evaluateAnyTargetMatches(room, targets);
        }

        return this.evaluateAllTargetsMatch(room, targets);
    }

    protected boolean evaluateAllTargetsMatch(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (!this.matchesTeam(room, roomUnit)) {
                return false;
            }
        }

        return true;
    }

    protected boolean evaluateAnyTargetMatches(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (this.matchesTeam(room, roomUnit)) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesTeam(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        return habbo != null
                && habbo.getHabboInfo().getGamePlayer() != null
                && habbo.getHabboInfo().getGamePlayer().getTeamColor().equals(this.teamColor);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.teamColor,
                this.userSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        try {
            String wiredData = set.getString("wired_data");

            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                this.teamColor = WiredConditionInputGuard.normalizeTeamColor(data.teamColor, GameTeamColors.RED);
                this.userSource = WiredConditionInputGuard.normalizeUserSource(data.userSource);
                this.quantifier = this.normalizeQuantifier(data.quantifier, QUANTIFIER_ANY);
            } else {
                if (!wiredData.equals(""))
                    this.teamColor = WiredConditionInputGuard.normalizeTeamColorType(Integer.parseInt(wiredData), GameTeamColors.RED);
                this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
                this.quantifier = QUANTIFIER_ANY;
            }
        } catch (Exception e) {
            this.teamColor = GameTeamColors.RED;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ALL;
        }
    }

    @Override
    public void onPickUp() {
        this.teamColor = GameTeamColors.RED;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
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
        message.appendInt(3);
        message.appendInt(this.teamColor.type);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 1) return false;
        int[] params = settings.getIntParams();
        this.teamColor = WiredConditionInputGuard.normalizeTeamColorType(params[0], GameTeamColors.RED);
        this.userSource = (params.length > 1) ? WiredConditionInputGuard.normalizeUserSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2], QUANTIFIER_ALL) : QUANTIFIER_ANY;

        return true;
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected int normalizeQuantifier(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }

        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    static class JsonData {
        GameTeamColors teamColor;
        int userSource;
        Integer quantifier;

        public JsonData(GameTeamColors teamColor, int userSource, int quantifier) {
            this.teamColor = teamColor;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
