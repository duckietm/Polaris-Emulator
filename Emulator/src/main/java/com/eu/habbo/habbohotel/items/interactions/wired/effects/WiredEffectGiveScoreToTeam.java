package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectGiveScoreToTeam extends InteractionWiredEffect {
    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_REMOVE = 1;
    public static final WiredEffectType type = WiredEffectType.GIVE_SCORE_TEAM;

    private int points;
    private int operation = OPERATION_ADD;
    private GameTeamColors teamColor = GameTeamColors.RED;

    public WiredEffectGiveScoreToTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public WiredEffectGiveScoreToTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        for (Game game : room.getGames()) {
            if (game != null && game.state.equals(GameState.RUNNING)) {
                GameTeam team = game.getTeam(this.teamColor);

                if (team != null) {
                    team.addTeamScore(this.getAppliedAmount(team));
                }
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
        return WiredManager.getGson().toJson(new JsonData(this.points, this.operation, this.teamColor, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.points = data.score;
            this.operation = this.normalizeOperation(data.operation);
            this.teamColor = data.team;
            this.setDelay(data.delay);
        }
        else {
            String[] data = set.getString("wired_data").split(";");

            if (data.length == 4) {
                this.points = Integer.parseInt(data[0]);
                this.operation = OPERATION_ADD;
                this.teamColor = GameTeamColors.fromType(Integer.parseInt(data[2]));
                this.setDelay(Integer.parseInt(data[3]));
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.points = 0;
        this.operation = OPERATION_ADD;
        this.teamColor = GameTeamColors.RED;
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
        message.appendInt(this.points);
        message.appendInt(this.operation);
        message.appendInt(this.teamColor.type);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 3) throw  new WiredSaveException("Invalid data");

        int points = settings.getIntParams()[0];

        if(points < 1 || points > 100)
            throw new WiredSaveException("Points is invalid");

        int operation = this.normalizeOperation(settings.getIntParams()[1]);

        int team = settings.getIntParams()[2];

        if(team < 1 || team > 4)
            throw new WiredSaveException("Team is invalid");

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.points = points;
        this.operation = operation;
        this.teamColor = GameTeamColors.fromType(team);
        this.setDelay(delay);

        return true;
    }

    private int normalizeOperation(int value) {
        return (value == OPERATION_REMOVE) ? OPERATION_REMOVE : OPERATION_ADD;
    }

    private int getAppliedAmount(GameTeam team) {
        if (this.operation != OPERATION_REMOVE) {
            return this.points;
        }

        return -Math.min(this.points, team.getTeamScore());
    }

    static class JsonData {
        int score;
        int operation;
        GameTeamColors team;
        int delay;

        public JsonData(int score, int operation, GameTeamColors team, int delay) {
            this.score = score;
            this.operation = operation;
            this.team = team;
            this.delay = delay;
        }
    }
}
