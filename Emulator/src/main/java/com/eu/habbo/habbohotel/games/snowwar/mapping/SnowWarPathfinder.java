package com.eu.habbo.habbohotel.games.snowwar.mapping;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarAttributes;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGamePlayer;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy neighbour-step pathfinder (README 5.7).
 */
public final class SnowWarPathfinder {

    public static final int MAX_PATHFIND_ITERATIONS = 50;

    private static final int[][] DIAGONAL_MOVE_POINTS = {
            {0, -1},   // N
            {1, -1},   // NE
            {1, 0},    // E
            {1, 1},    // SE
            {0, 1},    // S
            {-1, 1},   // SW
            {-1, 0},   // W
            {-1, -1}   // NW
    };

    private SnowWarPathfinder() {
    }

    public static boolean isValidTile(SnowWarGame game, SnowWarGamePlayer player, SnowWarPoint position) {
        SnowWarTile tile = game.getMap().getTile(position);

        if (tile == null || !tile.isWalkable()) {
            return false;
        }

        for (SnowWarGamePlayer otherPlayer : game.getActivePlayers()) {
            if (otherPlayer.getUserId() == player.getUserId()) {
                continue;
            }

            SnowWarAttributes attr = otherPlayer.getAttributes();

            if (attr.getNextGoal() != null) {
                if (attr.getNextGoal().equals(position)) {
                    return false;
                }
            } else {
                if (attr.getCurrentPosition().equals(position)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns the next tile in the path towards the player's walk goal,
     * or null when no neighbouring tile brings us closer / is available.
     */
    public static SnowWarPoint getNextDirection(SnowWarGame game, SnowWarGamePlayer player) {
        SnowWarAttributes attr = player.getAttributes();

        if (attr.getWalkGoal() == null) {
            return null;
        }

        List<SnowWarPoint> positions = new ArrayList<>();

        for (int[] direction : DIAGONAL_MOVE_POINTS) {
            SnowWarPoint candidate = attr.getCurrentPosition().add(direction[0], direction[1]);

            if (isValidTile(game, player, candidate)) {
                positions.add(candidate);
            }
        }

        if (positions.isEmpty()) {
            return null;
        }

        SnowWarPoint goal = attr.getWalkGoal();
        positions.sort((a, b) -> Integer.compare(a.getDistanceSquared(goal), b.getDistanceSquared(goal)));

        return positions.get(0);
    }
}
