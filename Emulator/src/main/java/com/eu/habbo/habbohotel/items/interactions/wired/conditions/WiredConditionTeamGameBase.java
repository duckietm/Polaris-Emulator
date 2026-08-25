package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

abstract class WiredConditionTeamGameBase extends InteractionWiredCondition {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int COMPARISON_LOWER = 0;
    protected static final int COMPARISON_EQUAL = 1;
    protected static final int COMPARISON_HIGHER = 2;
    protected static final int TEAM_TRIGGERER = 0;
    protected static final int MAX_SCORE = 1_000_000;

    private static final GameTeamColors[] SUPPORTED_TEAM_COLORS = new GameTeamColors[] {
            GameTeamColors.RED,
            GameTeamColors.GREEN,
            GameTeamColors.BLUE,
            GameTeamColors.YELLOW
    };

    protected WiredConditionTeamGameBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredConditionTeamGameBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            return false;
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

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getCurrentGame() == null) {
            return null;
        }

        Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());
        if (!this.isSupportedGame(game)) {
            return null;
        }

        GameTeam team = game.getTeamForHabbo(habbo);
        if (team == null) {
            return null;
        }

        return new UserGameContext(habbo, game, team);
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

    private boolean isSupportedGame(Game game) {
        return game != null
                && game.getState() != GameState.IDLE
                && (game instanceof FreezeGame || game instanceof BattleBanzaiGame);
    }

    protected static class UserGameContext {
        protected final Habbo habbo;
        protected final Game game;
        protected final GameTeam team;

        protected UserGameContext(Habbo habbo, Game game, GameTeam team) {
            this.habbo = habbo;
            this.game = game;
            this.team = team;
        }
    }
}
