package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Puts the selected users in a team of the wired, banzai or freeze game. The join mode picks the
 * team: the chosen one, the one with the fewest members, or a random one.
 *
 * <p>Int params {@code [game, team, user source, join mode]}; boxes saved with {@code [team, user
 * source]} or without the join mode join the chosen team.
 */
public class WiredEffectJoinTeam extends InteractionWiredEffect {
    static final int MODE_CHOSEN = 0;
    static final int MODE_SMALLEST = 1;
    static final int MODE_RANDOM = 2;
    private static final GameTeamColors[] TEAMS = {
        GameTeamColors.RED, GameTeamColors.GREEN, GameTeamColors.BLUE, GameTeamColors.YELLOW
    };
    private static final int TEAM_TYPE_WIRED = 0;
    private static final int TEAM_TYPE_BANZAI = 1;
    private static final int TEAM_TYPE_FREEZE = 2;
    public static final WiredEffectType type = WiredEffectType.JOIN_TEAM;

    private GameTeamColors teamColor = GameTeamColors.RED;
    private int teamType = TEAM_TYPE_WIRED;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int joinMode = MODE_CHOSEN;

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

            GameTeamColors team = this.resolveTeam(room.getGame(targetGameType), habbo);

            if (habbo.getHabboInfo().getGamePlayer() != null
                    && habbo.getHabboInfo().getCurrentGame() != null
                    && (habbo.getHabboInfo().getCurrentGame() != targetGameType
                            || habbo.getHabboInfo().getGamePlayer().getTeamColor() != team)
                    && currentGame != null) {
                currentGame.removeHabbo(habbo);
            }

            if (habbo.getHabboInfo().getGamePlayer() == null) {
                Game game = room.getGameOrCreate(targetGameType);
                if (game == null) {
                    continue;
                }
                game.addHabbo(habbo, team);
            }
        }
    }

    GameTeamColors resolveTeam(Game game, Habbo habbo) {
        return switch (this.joinMode) {
            case MODE_SMALLEST -> smallestTeam(game, habbo);
            case MODE_RANDOM -> TEAMS[ThreadLocalRandom.current().nextInt(TEAMS.length)];
            default -> this.teamColor;
        };
    }

    /**
     * The team with the fewest other members. A tie keeps the user in their own team, otherwise the
     * first of red, green, blue and yellow wins, so a room fills its teams in turn.
     */
    static GameTeamColors smallestTeam(Game game, Habbo habbo) {
        GameTeamColors current = null;
        if (game != null
                && habbo != null
                && habbo.getHabboInfo().getGamePlayer() != null
                && habbo.getHabboInfo().getCurrentGame() == game.getClass()) {
            current = habbo.getHabboInfo().getGamePlayer().getTeamColor();
        }

        GameTeamColors best = null;
        int bestSize = Integer.MAX_VALUE;
        for (GameTeamColors color : TEAMS) {
            GameTeam team = (game != null) ? game.getTeam(color) : null;
            int size = (team != null) ? team.getMembers().size() : 0;
            if (color == current) {
                size--;
            }
            if (size < bestSize || (size == bestSize && color == current)) {
                best = color;
                bestSize = size;
            }
        }
        return best;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.teamColor, this.teamType, this.getDelay(), this.userSource, this.joinMode));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            // Gson hands back null for a team name it does not know, and serializeWiredData then
            // dereferences it; an unknown team is the default one.
            this.teamColor = (data.team != null) ? data.team : GameTeamColors.RED;
            this.teamType = this.normalizeTeamType(data.teamType);
            this.userSource = data.userSource;
            this.joinMode = normalizeJoinMode(data.joinMode);
        } else {
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
            this.joinMode = MODE_CHOSEN;
        }
    }

    @Override
    public void onPickUp() {
        this.teamColor = GameTeamColors.RED;
        this.teamType = TEAM_TYPE_WIRED;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.joinMode = MODE_CHOSEN;
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
        message.appendInt(this.teamColor.type);
        message.appendInt(this.teamType);
        message.appendInt(this.joinMode);
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
        // Official layout: [team, team type]; ours follows: [join mode]. The user source is the official
        // source slot.
        if (settings.getIntParams().length < 1) throw new WiredSaveException("invalid data");

        int team = settings.getIntParams()[0];
        int teamType = (settings.getIntParams().length > 1)
                ? this.normalizeTeamType(settings.getIntParams()[1])
                : TEAM_TYPE_WIRED;
        int joinMode =
                (settings.getIntParams().length > 2) ? normalizeJoinMode(settings.getIntParams()[2]) : MODE_CHOSEN;
        int userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        if (team < 1 || team > 4) throw new WiredSaveException("Team is invalid");

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.teamType = teamType;
        this.userSource = WiredMovementPayloadGuard.userSource(userSource);
        this.joinMode = joinMode;
        this.teamColor = GameTeamColors.fromType(team);
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static int normalizeJoinMode(int value) {
        return (value == MODE_SMALLEST || value == MODE_RANDOM) ? value : MODE_CHOSEN;
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
        int joinMode;

        public JsonData(GameTeamColors team, int teamType, int delay, int userSource, int joinMode) {
            this.team = team;
            this.teamType = teamType;
            this.delay = delay;
            this.userSource = userSource;
            this.joinMode = joinMode;
        }
    }
}
