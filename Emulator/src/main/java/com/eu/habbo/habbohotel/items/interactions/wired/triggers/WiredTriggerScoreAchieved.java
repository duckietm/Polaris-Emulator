package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.lang.ref.WeakReference;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerScoreAchieved extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.SCORE_ACHIEVED;
    static final int MAX_SCORE = 1_000_000;
    private int score = 0;
    private int teamType = GameTeamColors.NONE.type;
    /** The round this box already fired in: AIR's Score Achieved fires once per game round. */
    private WeakReference<Game> spentGame = new WeakReference<>(null);

    private int spentRound = Integer.MIN_VALUE;

    public WiredTriggerScoreAchieved(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerScoreAchieved(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        int points = event.getScore();
        int amountAdded = event.getScoreAdded();

        // Check if this score addition crossed the threshold
        if (!(points - amountAdded < this.score && points >= this.score)) {
            return false;
        }

        Habbo habbo =
                event.getActor().map(unit -> event.getRoom().getHabbo(unit)).orElse(null);

        if (this.teamType != GameTeamColors.NONE.type
                && (habbo == null
                        || habbo.getHabboInfo().getGamePlayer() == null
                        || habbo.getHabboInfo().getGamePlayer().getTeamColor().type != this.teamType)) {
            return false;
        }

        return this.spendRound(gameOf(event.getRoom(), habbo));
    }

    /**
     * The first team over the threshold uses the box up for the rest of the round, even when a condition then stops
     * the stack; a new game start (or a save with another score or team) makes it fire again.
     */
    synchronized boolean spendRound(Game game) {
        if (game == null) {
            return true;
        }

        if (this.spentGame.get() == game && this.spentRound == game.getStartTime()) {
            return false;
        }

        this.spentGame = new WeakReference<>(game);
        this.spentRound = game.getStartTime();
        return true;
    }

    private static Game gameOf(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null) {
            return null;
        }

        Class<? extends Game> current = habbo.getHabboInfo().getCurrentGame();
        return current == null ? null : room.getGame(current);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.score, this.teamType));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        JsonData data = parseData(wiredData);
        this.score = data.score;
        this.teamType = data.teamType;
    }

    static JsonData parseData(String wiredData) {
        if (wiredData == null || wiredData.isBlank()) {
            return new JsonData(0, GameTeamColors.NONE.type);
        }

        try {
            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                return data != null
                        ? new JsonData(clampScore(data.score), normalizeTeamType(data.teamType))
                        : new JsonData(0, GameTeamColors.NONE.type);
            }

            return new JsonData(clampScore(Integer.parseInt(wiredData)), GameTeamColors.NONE.type);
        } catch (RuntimeException e) {
            return new JsonData(0, GameTeamColors.NONE.type);
        }
    }

    @Override
    public void onPickUp() {
        this.score = 0;
        this.teamType = GameTeamColors.NONE.type;
    }

    @Override
    public WiredTriggerType getType() {
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
        message.appendInt(2);
        message.appendInt(this.score);
        message.appendInt(this.teamType);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 1) return false;
        int score = clampScore(settings.getIntParams()[0]);
        int teamType = (settings.getIntParams().length > 1)
                ? normalizeTeamType(settings.getIntParams()[1])
                : GameTeamColors.NONE.type;
        if (score != this.score || teamType != this.teamType) {
            synchronized (this) {
                this.spentGame = new WeakReference<>(null);
                this.spentRound = Integer.MIN_VALUE;
            }
        }
        this.score = score;
        this.teamType = teamType;
        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static int clampScore(int value) {
        if (value < 0) {
            return 0;
        }

        return Math.min(value, MAX_SCORE);
    }

    static int normalizeTeamType(int value) {
        if (value >= GameTeamColors.RED.type && value <= GameTeamColors.YELLOW.type) {
            return value;
        }

        return GameTeamColors.NONE.type;
    }

    static class JsonData {
        int score;
        int teamType;

        public JsonData(int score, int teamType) {
            this.score = score;
            this.teamType = teamType;
        }
    }
}
