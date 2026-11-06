package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import polaris.hotel.games.Game;
import polaris.hotel.games.GameState;
import polaris.hotel.games.GameTeam;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.games.battlebanzai.BattleBanzaiGame;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;

abstract class WiredConditionTeamGameBase extends InteractionWiredCondition {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int COMPARISON_LOWER = 0;
    protected static final int COMPARISON_EQUAL = 1;
    protected static final int COMPARISON_HIGHER = 2;
    protected static final int TEAM_TRIGGERER = 0;
    protected static final int MAX_SCORE = 1_000_000;

    private static final GameTeamColors[] SUPPORTED_TEAM_COLORS =
            new GameTeamColors[] {GameTeamColors.RED, GameTeamColors.GREEN, GameTeamColors.BLUE, GameTeamColors.YELLOW};

    protected WiredConditionTeamGameBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredConditionTeamGameBase(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    protected List<RoomUnit> resolveUsers(WiredContext ctx, int userSource) {
        Map<Integer, RoomUnit> deduplicated = new LinkedHashMap<>();

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, userSource)) {
            if (roomUnit != null) {
                deduplicated.putIfAbsent(roomUnit.getId(), roomUnit);
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    protected boolean matchesQuantifier(List<RoomUnit> users, int quantifier, Predicate<RoomUnit> predicate) {
        if (users.isEmpty()) {
            return WiredQuantifier.overEmpty(quantifier != QUANTIFIER_ANY);
        }

        if (quantifier == QUANTIFIER_ANY) {
            return users.stream().anyMatch(predicate);
        }

        return users.stream().allMatch(predicate);
    }

    protected int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected int normalizeComparison(int value) {
        switch (value) {
            case COMPARISON_LOWER:
            case COMPARISON_HIGHER:
                return value;
            default:
                return COMPARISON_EQUAL;
        }
    }

    protected int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    protected int normalizePlacement(int value) {
        if (value < 1 || value > 4) {
            return 1;
        }

        return value;
    }

    protected int normalizeScore(int value) {
        if (value < 0) {
            return 0;
        }

        return Math.min(value, MAX_SCORE);
    }

    protected int normalizeExplicitTeamType(int value) {
        GameTeamColors color = GameTeamColors.fromType(value);
        return (color.type >= GameTeamColors.RED.type && color.type <= GameTeamColors.YELLOW.type)
                ? color.type
                : GameTeamColors.RED.type;
    }

    protected int normalizeRankTeamType(int value) {
        if (value == TEAM_TRIGGERER) {
            return TEAM_TRIGGERER;
        }

        return this.normalizeExplicitTeamType(value);
    }

    protected GameTeamColors resolveConfiguredTeamColor(int value) {
        return GameTeamColors.fromType(this.normalizeExplicitTeamType(value));
    }

    protected boolean compareValue(int actual, int expected, int comparison) {
        switch (comparison) {
            case COMPARISON_LOWER:
                return actual < expected;
            case COMPARISON_HIGHER:
                return actual > expected;
            default:
                return actual == expected;
        }
    }

    protected UserGameContext resolveUserGameContext(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return null;
        }

        User user = room.getUser(roomUnit);
        if (user == null || user.getUserInfo() == null || user.getUserInfo().getCurrentGame() == null) {
            return null;
        }

        Game game = room.getGame(user.getUserInfo().getCurrentGame());
        if (!this.isSupportedGame(game)) {
            return null;
        }

        GameTeam team = game.getTeamForUser(user);
        if (team == null) {
            return null;
        }

        return new UserGameContext(user, game, team);
    }

    protected int getTeamRank(Game game, GameTeam team) {
        if (game == null || team == null) {
            return Integer.MAX_VALUE;
        }

        int rank = 1;
        int targetScore = team.getTotalScore();

        for (GameTeamColors teamColor : SUPPORTED_TEAM_COLORS) {
            GameTeam otherTeam = game.getTeam(teamColor);
            if (otherTeam != null && otherTeam != team && otherTeam.getTotalScore() > targetScore) {
                rank++;
            }
        }

        return rank;
    }

    /**
     * The team game running in the room, for boxes about a named team rather than about a user:
     * such a box asks about the room's game, whoever set it off - or nobody, on a timer.
     */
    protected Game resolveActiveRoomGame(Room room) {
        if (room == null || room.getGames() == null) {
            return null;
        }

        for (Game game : room.getGames()) {
            if (this.isSupportedGame(game)) {
                return game;
            }
        }

        return null;
    }

    private boolean isSupportedGame(Game game) {
        return game != null
                && game.getState() != GameState.IDLE
                && (game instanceof FreezeGame || game instanceof BattleBanzaiGame);
    }

    protected static class UserGameContext {
        protected final User user;
        protected final Game game;
        protected final GameTeam team;

        protected UserGameContext(User user, Game game, GameTeam team) {
            this.user = user;
            this.game = game;
            this.team = team;
        }
    }
}
