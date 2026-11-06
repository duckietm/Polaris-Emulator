package polaris.hotel.games.snowwar.mapping;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.games.snowwar.SnowWarAttributes;
import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.SnowWarPoint;

/**
 * Greedy neighbour-step pathfinder (README 5.7).
 */
public final class SnowWarPathfinder {

    public static final int MAX_PATHFIND_ITERATIONS = 50;

    private static final int[][] DIAGONAL_MOVE_POINTS = {
        {0, -1}, // N
        {1, -1}, // NE
        {1, 0}, // E
        {1, 1}, // SE
        {0, 1}, // S
        {-1, 1}, // SW
        {-1, 0}, // W
        {-1, -1} // NW
    };

    private SnowWarPathfinder() {}

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

            if (direction[0] != 0
                    && direction[1] != 0
                    && !isValidTile(game, player, attr.getCurrentPosition().add(direction[0], 0))
                    && !isValidTile(game, player, attr.getCurrentPosition().add(0, direction[1]))) {
                continue;
            }

            if (isValidTile(game, player, candidate)) {
                positions.add(candidate);
            }
        }

        if (positions.isEmpty()) {
            return null;
        }

        SnowWarPoint goal = attr.getWalkGoal();
        positions.sort((a, b) -> Integer.compare(a.getDistanceSquared(goal), b.getDistanceSquared(goal)));

        // Only step if it brings us strictly closer to the goal; otherwise
        // stop where we are. Without this, an unreachable goal makes the
        // greedy step oscillate between two equally-close tiles.
        if (positions.get(0).getDistanceSquared(goal)
                >= attr.getCurrentPosition().getDistanceSquared(goal)) {
            return null;
        }

        return positions.get(0);
    }
}
