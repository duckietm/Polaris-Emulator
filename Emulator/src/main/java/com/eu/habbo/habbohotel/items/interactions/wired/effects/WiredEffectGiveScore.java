package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WiredEffectGiveScore extends InteractionWiredEffect {
    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_REMOVE = 1;

    /** Upper bound of the {@code wiredfurni.params.setpoints2} slider the client renders. */
    private static final int MAXIMUM_SCORE = 1000;

    /**
     * Upper bound of the {@code wiredfurni.params.settimesingame} slider. The slider carries one
     * position above this one, which is stored as {@link #UNLIMITED_TIMES_IN_GAME}.
     */
    private static final int MAXIMUM_TIMES_IN_GAME = 10;

    /** A per-game limit of zero lets the effect award score as often as it is triggered. */
    private static final int UNLIMITED_TIMES_IN_GAME = 0;

    public static final WiredEffectType type = WiredEffectType.GIVE_SCORE;

    private int score;
    private int operation = OPERATION_ADD;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int timesInGame = UNLIMITED_TIMES_IN_GAME;

    /**
     * How often this effect has already awarded each player during the game identified by
     * {@link #countedGameStartTime}. Runtime state: never persisted, never sent to the client.
     */
    private final Map<Integer, Integer> awardsThisGame = new HashMap<>();

    private int countedGameStartTime = -1;

    public WiredEffectGiveScore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveScore(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null || habbo.getHabboInfo().getCurrentGame() == null) continue;

            Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());

            if (game == null) continue;

            if (habbo.getHabboInfo().getGamePlayer() == null) continue;

            this.forgetAwardsOfEarlierGames(game);

            if (this.hasReachedLimit(habbo)) continue;

            habbo.getHabboInfo().getGamePlayer().addScore(this.getAppliedAmount(), true);
            this.countAward(habbo);
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
                .toJson(new JsonData(this.score, this.operation, this.getDelay(), this.userSource, this.timesInGame));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.score = data.score;
            this.operation = this.normalizeOperation(data.operation);
            this.setDelay(data.delay);
            this.userSource = data.userSource;
            this.timesInGame = this.normalizeTimesInGame(data.timesInGame);
        } else {
            String[] data = wiredData.split(";");

            if (data.length == 3) {
                this.score = Integer.parseInt(data[0]);
                this.operation = OPERATION_ADD;
                this.setDelay(Integer.parseInt(data[2]));
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.score = 0;
        this.operation = OPERATION_ADD;
        this.setDelay(0);
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.timesInGame = UNLIMITED_TIMES_IN_GAME;
        this.awardsThisGame.clear();
        this.countedGameStartTime = -1;
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectGiveScore.type;
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
        message.appendInt(this.score);
        message.appendInt(this.operation);
        message.appendInt(this.userSource);
        message.appendInt(this.timesInGame);
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
        if (settings.getIntParams().length < 3) throw new WiredSaveException("Invalid data");

        int score = settings.getIntParams()[0];

        if (score < 1 || score > MAXIMUM_SCORE) throw new WiredSaveException("Score is invalid");

        int operation = this.normalizeOperation(settings.getIntParams()[1]);

        this.userSource = settings.getIntParams()[2];

        // Older clients save three params; those boxes keep awarding score without a per-game limit.
        int timesInGame = settings.getIntParams().length > 3
                ? this.normalizeTimesInGame(settings.getIntParams()[3])
                : UNLIMITED_TIMES_IN_GAME;

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.score = score;
        this.operation = operation;

        if (timesInGame != this.timesInGame) {
            this.awardsThisGame.clear();
        }

        this.timesInGame = timesInGame;
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeOperation(int value) {
        return (value == OPERATION_REMOVE) ? OPERATION_REMOVE : OPERATION_ADD;
    }

    private int normalizeTimesInGame(int value) {
        if (value <= UNLIMITED_TIMES_IN_GAME) return UNLIMITED_TIMES_IN_GAME;

        return Math.min(value, MAXIMUM_TIMES_IN_GAME);
    }

    /** The counters belong to one game; a later game start makes the previous tally irrelevant. */
    private void forgetAwardsOfEarlierGames(Game game) {
        if (game.getStartTime() == this.countedGameStartTime) return;

        this.countedGameStartTime = game.getStartTime();
        this.awardsThisGame.clear();
    }

    private boolean hasReachedLimit(Habbo habbo) {
        if (this.timesInGame == UNLIMITED_TIMES_IN_GAME) return false;

        return this.awardsThisGame.getOrDefault(habbo.getHabboInfo().getId(), 0) >= this.timesInGame;
    }

    private void countAward(Habbo habbo) {
        if (this.timesInGame == UNLIMITED_TIMES_IN_GAME) return;

        this.awardsThisGame.merge(habbo.getHabboInfo().getId(), 1, Integer::sum);
    }

    private int getAppliedAmount() {
        return (this.operation == OPERATION_REMOVE) ? -this.score : this.score;
    }

    static class JsonData {
        int score;
        int operation;
        int delay;
        int userSource;
        int timesInGame;

        public JsonData(int score, int operation, int delay, int userSource, int timesInGame) {
            this.score = score;
            this.operation = operation;
            this.delay = delay;
            this.userSource = userSource;
            this.timesInGame = timesInGame;
        }
    }
}
