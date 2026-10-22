package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
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
    /** Official "times in game": how often this box scores for its team per round (0: unlimited). */
    private int timesPerGame = WiredScorePerGame.UNLIMITED;

    private final WiredScorePerGame scored = new WiredScorePerGame();

    public WiredEffectGiveScoreToTeam(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

                if (team != null && this.scored.tryCount(game, this.teamColor, this.timesPerGame)) {
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
        JsonData data = new JsonData(this.points, this.operation, this.teamColor, this.getDelay());
        data.timesPerGame = this.timesPerGame;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.points = data.score;
            this.operation = this.normalizeOperation(data.operation);
            // Gson hands back null for a team name it does not know, and serializeWiredData then
            // dereferences it; an unknown team is the default one.
            this.teamColor = (data.team != null) ? data.team : GameTeamColors.RED;
            this.setDelay(data.delay);
            this.timesPerGame = WiredScorePerGame.normalize(data.timesPerGame);
        } else {
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
        this.timesPerGame = WiredScorePerGame.UNLIMITED;
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
        // Official layout: [points (negative to take them), times in game (0: unlimited), team].
        message.appendInt(3);
        message.appendInt(this.operation == OPERATION_REMOVE ? -this.points : this.points);
        message.appendInt(this.timesPerGame);
        message.appendInt(this.teamColor.type);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // Official layout: [points (negative to take them), times in game (0: unlimited), team].
        if (settings.getIntParams().length < 3) throw new WiredSaveException("Invalid data");

        int signed = settings.getIntParams()[0];
        int points = Math.abs(signed);

        if (points < 1 || points > 1000) throw new WiredSaveException("Points is invalid");

        int operation = (signed < 0) ? OPERATION_REMOVE : OPERATION_ADD;
        this.timesPerGame = WiredScorePerGame.normalize(settings.getIntParams()[1]);

        int team = settings.getIntParams()[2];

        if (team < 1 || team > 4) throw new WiredSaveException("Team is invalid");

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
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
        int timesPerGame;

        public JsonData(int score, int operation, GameTeamColors team, int delay) {
            this.score = score;
            this.operation = operation;
            this.team = team;
            this.delay = delay;
        }
    }
}
