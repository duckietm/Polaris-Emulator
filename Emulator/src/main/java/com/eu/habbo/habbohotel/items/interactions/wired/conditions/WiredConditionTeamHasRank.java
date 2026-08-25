package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionTeamHasRank extends WiredConditionTeamGameBase {
    public static final WiredConditionType type = WiredConditionType.TEAM_HAS_RANK;

    private int teamType = GameTeamColors.RED.type;
    private int placement = 1;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionTeamHasRank(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTeamHasRank(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        List<RoomUnit> users = this.resolveUsers(ctx, this.userSource);

        return this.matchesQuantifier(users, this.quantifier, roomUnit -> this.matchesUser(ctx, room, roomUnit));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.teamType,
                this.placement,
                this.userSource,
                this.quantifier
        ));
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

        this.teamType = this.normalizeRankTeamType(data.teamType);
        this.placement = this.normalizePlacement(data.placement);
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
        message.appendInt(this.placement);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.resetSettings();

        if (params.length > 0) this.teamType = this.normalizeRankTeamType(params[0]);
        if (params.length > 1) this.placement = this.normalizePlacement(params[1]);
        if (params.length > 2) this.userSource = this.normalizeUserSource(params[2]);
        if (params.length > 3) this.quantifier = this.normalizeQuantifier(params[3]);

        return true;
    }

    private boolean matchesUser(WiredContext ctx, Room room, RoomUnit roomUnit) {
        UserGameContext context = this.resolveUserGameContext(room, roomUnit);
        if (context == null) {
            return false;
        }

        GameTeamColors requiredTeam = this.resolveRequiredTeamColor(ctx, room, context.game);
        if (requiredTeam == GameTeamColors.NONE || context.team.teamColor != requiredTeam) {
            return false;
        }

        GameTeam team = context.game.getTeam(requiredTeam);
        if (team == null) {
            return false;
        }

        return this.getTeamRank(context.game, team) == this.placement;
    }

    private GameTeamColors resolveRequiredTeamColor(WiredContext ctx, Room room, com.eu.habbo.habbohotel.games.Game game) {
        if (this.teamType == TEAM_TRIGGERER) {
            RoomUnit actor = ctx.actor().orElse(null);
            UserGameContext triggererContext = this.resolveUserGameContext(room, actor);

            if (triggererContext == null || triggererContext.game != game) {
                return GameTeamColors.NONE;
            }

            return triggererContext.team.teamColor;
        }

        return this.resolveConfiguredTeamColor(this.teamType);
    }

    private void resetSettings() {
        this.teamType = GameTeamColors.RED.type;
        this.placement = 1;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    static class JsonData {
        int teamType;
        int placement;
        int userSource;
        int quantifier;

        public JsonData(int teamType, int placement, int userSource, int quantifier) {
            this.teamType = teamType;
            this.placement = placement;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
