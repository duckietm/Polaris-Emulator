package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.BASIC_MOVEMENT_COST;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.DIAGONAL_MOVEMENT_COST;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.Rotation.DIAGONAL_DIRECTIONS;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.Rotation.DIRECTIONS;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.TileValidator.isOutOfBounds;

import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The old pathfinder's neighbour lookup. The pathfinder no longer uses it (see GridSearch); it stays
 * for plugins built against earlier releases.
 */
@Deprecated
public class AdjacentTileFinder {

    AdjacentTileFinder() {}

    public static Set<RoomTile> getAdjacent(
            PathfinderContext context,
            RoomTile node,
            RoomTile nextTile,
            RoomUnit unit,
            boolean canMoveDiagonally,
            boolean retroStyleDiagonals) {
        short x = node.getX();
        short y = node.getY();
        Set<RoomTile> adj = new HashSet<>();
        addDirectionAdjacent(context, node, nextTile, unit, x, y, adj);

        if (canMoveDiagonally) {
            addDiagonalAdjacent(context, node, nextTile, unit, x, y, adj, retroStyleDiagonals);
        }

        return adj;
    }

    public static void addDirectionAdjacent(
            PathfinderContext context,
            RoomTile node,
            RoomTile nextTile,
            RoomUnit unit,
            short x,
            short y,
            Set<RoomTile> adj) {
        for (short[] direction : DIRECTIONS) {
            short newX = (short) (x + direction[0]);
            short newY = (short) (y + direction[1]);

            if (isOutOfBounds(context.getRoom().getLayout(), newX, newY)) {
                continue;
            }
            RoomTile temp = findTile(context, newX, newY);
            if (temp != null) {
                addAdjacent(node, nextTile, unit, temp, adj, false);
            }
        }
    }

    public static void addDiagonalAdjacent(
            PathfinderContext context,
            RoomTile node,
            RoomTile nextTile,
            RoomUnit unit,
            short x,
            short y,
            Set<RoomTile> adj,
            boolean retroStyleDiagonals) {
        for (short[] direction : DIAGONAL_DIRECTIONS) {
            short newX = (short) (x + direction[0]);
            short newY = (short) (y + direction[1]);

            if (isOutOfBounds(context.getRoom().getLayout(), newX, newY)
                    || (!retroStyleDiagonals && isBlockedDiagonal(context, x, y, newX, newY))) {
                continue;
            }

            RoomTile temp = findTile(context, newX, newY);
            if (TileValidator.isWalkableOrGoal(context, temp)) {
                addAdjacent(node, nextTile, unit, temp, adj, true);
            }
        }
    }

    public static boolean isBlockedDiagonal(PathfinderContext context, short x, short y, short newX, short newY) {

        RoomTile offX = findTile(context, newX, y);
        RoomTile offY = findTile(context, x, newY);

        return offX == null || offY == null || (!offX.isWalkable() && !offY.isWalkable());
    }

    private static void addAdjacent(
            RoomTile node, RoomTile nextTile, RoomUnit unit, RoomTile temp, Set<RoomTile> adj, boolean isDiagonal) {
        if (temp != null
                && (temp.getState() != RoomTileState.SIT || nextTile.getStackHeight() - node.getStackHeight() <= 2.0)
                && canWalkOn(temp, unit)) {
            temp.isDiagonally(isDiagonal);
            adj.add(temp);
        }
    }

    private static boolean canWalkOn(RoomTile tile, RoomUnit unit) {
        return tile != null
                && ((tile.getState() != RoomTileState.BLOCKED && tile.getState() != RoomTileState.INVALID)
                        || unit.canOverrideTile(tile));
    }

    /**
     * Finds and returns a RoomTile based on the provided coordinates, using caching for efficiency.
     * If the tile is found in the cache, it is returned directly. Otherwise, the tile is retrieved
     * from the room layout, copied, cached, and then returned.
     *
     * @param context The pathfinder context containing the tile cache and other data structures.
     * @param x       The x-coordinate of the tile to be found.
     * @param y       The y-coordinate of the tile to be found.
     * @return The RoomTile corresponding to the given coordinates, or null if no tile exists at the
     * specified location.
     */
    public static RoomTile findTile(PathfinderContext context, short x, short y) {
        long key = generateTileKey(x, y);
        Map<Long, RoomTile> tileCache = context.getTileCache();
        RoomTile tile = tileCache.get(key);
        if (tile != null) {
            return tile;
        }

        tile = context.getRoom().getLayout().getTile(x, y);
        if (tile == null) {
            return null;
        }

        tile = tile.copy();
        tileCache.put(key, tile);
        return tile;
    }

    /**
     * Generates a unique long key for a tile based on its x and y coordinates. This key can be used
     * for identifying tiles in a map or cache efficiently.
     *
     * @param x The x-coordinate of the tile.
     * @param y The y-coordinate of the tile.
     * @return A long value that uniquely identifies the tile based on the provided x and y
     * coordinates.
     */
    private static long generateTileKey(short x, short y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    public static void calculateCost(
            PathfinderContext context, RoomTile currentAdj, RoomTile current, PriorityQueue<RoomTile> openList) {
        if (!openList.contains(currentAdj)) {
            updateAdj(context, currentAdj, current, openList);
            return;
        }

        if (currentAdj.getgCosts() > calculateGCosts(currentAdj, current)) {
            currentAdj.setPrevious(current);
            currentAdj.setgCosts(current, getCost(currentAdj));
        }
    }

    public static int calculateGCosts(RoomTile tile, RoomTile previousRoomTile) {
        if (tile.isDiagonally()) {
            return previousRoomTile.getgCosts() + DIAGONAL_MOVEMENT_COST;
        }

        return previousRoomTile.getgCosts() + BASIC_MOVEMENT_COST;
    }

    public static void updateAdj(
            PathfinderContext context, RoomTile currentAdj, RoomTile current, PriorityQueue<RoomTile> openList) {
        currentAdj.setPrevious(current);
        RoomTile tile = AdjacentTileFinder.findTile(
                context, context.getNewTile().getX(), context.getNewTile().getY());
        if (tile == null) {
            return;
        }
        currentAdj.sethCosts(tile, getCost(current));
        currentAdj.setgCosts(current, getCost(currentAdj));
        openList.add(currentAdj);
    }

    private static int getCost(RoomTile tile) {
        return tile.isDiagonally() ? DIAGONAL_MOVEMENT_COST : BASIC_MOVEMENT_COST;
    }
}
