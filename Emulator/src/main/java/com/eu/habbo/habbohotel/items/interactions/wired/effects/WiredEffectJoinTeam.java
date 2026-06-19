package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectJoinTeam extends InteractionWiredEffect {
    private static final int TEAM_TYPE_WIRED = 0;
    private static final int TEAM_TYPE_BANZAI = 1;
    private static final int TEAM_TYPE_FREEZE = 2;
    public static final WiredEffectType type = WiredEffectType.JOIN_TEAM;

    private GameTeamColors teamColor = GameTeamColors.RED;
    private int teamType = TEAM_TYPE_WIRED;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectJoinTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectJoinTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        Class<? extends Game> targetGameType = this.resolveGameType();

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null) continue;

            Game currentGame = null;
            if (habbo.getHabboInfo().getCurrentGame() != null) {
                currentGame = room.getGame(habbo.getHabboInfo().getCurrentGame());
            }

            if (habbo.getHabboInfo().getGamePlayer() != null
                    && habbo.getHabboInfo().getCurrentGame() != null
                    && (habbo.getHabboInfo().getCurrentGame() != targetGameType
                    || habbo.getHabboInfo().getGamePlayer().getTeamColor() != this.teamColor)
                    && currentGame != null) {
                currentGame.removeHabbo(habbo);
            }

            if(habbo.getHabboInfo().getGamePlayer() == null) {
                Game game = room.getGameOrCreate(targetGameType);
                if (game == null) {
                    continue;
                }
                game.addHabbo(habbo, this.teamColor);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.teamColor, this.teamType, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.teamColor = data.team;
            this.teamType = this.normalizeTeamType(data.teamType);
            this.userSource = data.userSource;
        }
        else {
            String[] data = set.getString("wired_data").split("\t");

            if (data.length >= 1) {
                this.setDelay(Integer.parseInt(data[0]));

                if (data.length >= 2) {
                    this.teamColor = GameTeamColors.fromType(Integer.parseInt(data[1]));
                }
            }

            this.needsUpdate(true);
            this.teamType = TEAM_TYPE_WIRED;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.teamColor = GameTeamColors.RED;
        this.teamType = TEAM_TYPE_WIRED;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
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
        message.appendInt(this.teamType);
        message.appendInt(this.teamColor.type);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 2) throw new WiredSaveException("invalid data");

        if (settings.getIntParams().length > 2) {
            this.teamType = this.normalizeTeamType(settings.getIntParams()[0]);
            this.userSource = settings.getIntParams()[2];
        } else {
            this.teamType = TEAM_TYPE_WIRED;
            this.userSource = settings.getIntParams()[1];
        }

        int team = (settings.getIntParams().length > 2) ? settings.getIntParams()[1] : settings.getIntParams()[0];

        if(team < 1 || team > 4)
            throw new WiredSaveException("Team is invalid");

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.teamColor = GameTeamColors.fromType(team);
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeTeamType(int value) {
        if (value == TEAM_TYPE_BANZAI || value == TEAM_TYPE_FREEZE) {
            return value;
        }

        return TEAM_TYPE_WIRED;
    }

    private Class<? extends Game> resolveGameType() {
        switch (this.teamType) {
            case TEAM_TYPE_BANZAI:
                return BattleBanzaiGame.class;
            case TEAM_TYPE_FREEZE:
                return FreezeGame.class;
            default:
                return WiredGame.class;
        }
    }

    static class JsonData {
        GameTeamColors team;
        int teamType;
        int delay;
        int userSource;

        public JsonData(GameTeamColors team, int teamType, int delay, int userSource) {
            this.team = team;
            this.teamType = teamType;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
