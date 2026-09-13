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
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WiredEffectGiveScoreToTeam extends InteractionWiredEffect {
    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_REMOVE = 1;

    /** Upper bound of the {@code wiredfurni.params.setpoints2} slider the client renders. */
    private static final int MAXIMUM_POINTS = 1000;

    /**
     * Upper bound of the {@code wiredfurni.params.settimesingame} slider. The slider carries one
     * position above this one, which is stored as {@link #UNLIMITED_TIMES_IN_GAME}.
     */
    private static final int MAXIMUM_TIMES_IN_GAME = 10;

    /** A per-game limit of zero lets the effect award points as often as it is triggered. */
    private static final int UNLIMITED_TIMES_IN_GAME = 0;

    public static final WiredEffectType type = WiredEffectType.GIVE_SCORE_TEAM;

    private int points;
    private int operation = OPERATION_ADD;
    private GameTeamColors teamColor = GameTeamColors.RED;
    private int timesInGame = UNLIMITED_TIMES_IN_GAME;

    /**
     * Awards already made, keyed by the start time of the game they belong to. There is no
     * triggering user on this effect, so the limit counts the awards of the box itself. Runtime
     * state: never persisted, never sent to the client.
     */
    private final Map<Integer, Integer> awardsByGameStart = new HashMap<>();

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

        this.forgetFinishedGames(room);

        for (Game game : room.getGames()) {
            if (game != null && game.state.equals(GameState.RUNNING)) {
                GameTeam team = game.getTeam(this.teamColor);

                if (team != null && !this.hasReachedLimit(game)) {
                    team.addTeamScore(this.getAppliedAmount(team));
                    this.countAward(game);
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
        return WiredManager.getGson()
                .toJson(new JsonData(this.points, this.operation, this.teamColor, this.getDelay(), this.timesInGame));
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
            this.timesInGame = this.normalizeTimesInGame(data.timesInGame);
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
        this.timesInGame = UNLIMITED_TIMES_IN_GAME;
        this.awardsByGameStart.clear();
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
        message.appendInt(4);
        message.appendInt(this.points);
        message.appendInt(this.operation);
        message.appendInt(this.teamColor.type);
        message.appendInt(this.timesInGame);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.getIntParams().length < 3) throw new WiredSaveException("Invalid data");

        int points = settings.getIntParams()[0];

        if (points < 1 || points > MAXIMUM_POINTS) throw new WiredSaveException("Points is invalid");

        int operation = this.normalizeOperation(settings.getIntParams()[1]);

        int team = settings.getIntParams()[2];

        if (team < 1 || team > 4) throw new WiredSaveException("Team is invalid");

        // Older clients save three params; those boxes keep awarding points without a per-game limit.
        int timesInGame = settings.getIntParams().length > 3
                ? this.normalizeTimesInGame(settings.getIntParams()[3])
                : UNLIMITED_TIMES_IN_GAME;

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.points = points;
        this.operation = operation;
        this.teamColor = GameTeamColors.fromType(team);

        if (timesInGame != this.timesInGame) {
            this.awardsByGameStart.clear();
        }

        this.timesInGame = timesInGame;
        this.setDelay(delay);

        return true;
    }

    private int normalizeOperation(int value) {
        return (value == OPERATION_REMOVE) ? OPERATION_REMOVE : OPERATION_ADD;
    }

    private int normalizeTimesInGame(int value) {
        if (value <= UNLIMITED_TIMES_IN_GAME) return UNLIMITED_TIMES_IN_GAME;

        return Math.min(value, MAXIMUM_TIMES_IN_GAME);
    }

    /** Keeps the tally bounded: a game that is no longer in the room can never be awarded again. */
    private void forgetFinishedGames(Room room) {
        if (this.awardsByGameStart.isEmpty()) return;

        Set<Integer> live = new HashSet<>();

        for (Game game : room.getGames()) {
            if (game != null) live.add(game.getStartTime());
        }

        this.awardsByGameStart.keySet().retainAll(live);
    }

    private boolean hasReachedLimit(Game game) {
        if (this.timesInGame == UNLIMITED_TIMES_IN_GAME) return false;

        return this.awardsByGameStart.getOrDefault(game.getStartTime(), 0) >= this.timesInGame;
    }

    private void countAward(Game game) {
        if (this.timesInGame == UNLIMITED_TIMES_IN_GAME) return;

        this.awardsByGameStart.merge(game.getStartTime(), 1, Integer::sum);
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
        int timesInGame;

        public JsonData(int score, int operation, GameTeamColors team, int delay, int timesInGame) {
            this.score = score;
            this.operation = operation;
            this.team = team;
            this.delay = delay;
            this.timesInGame = timesInGame;
        }
    }
}
