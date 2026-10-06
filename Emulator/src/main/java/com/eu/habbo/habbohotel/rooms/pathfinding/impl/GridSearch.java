package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.BASIC_MOVEMENT_COST;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.DIAGONAL_MOVEMENT_COST;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.DISTANCE_DOOR_THRESHOLD;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.TIMEOUT_CHECK_INTERVAL;

import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A* over a room's tiles. Costs, parents and the open list live in int arrays reused between
 * searches (stamped per search instead of cleared), so a search allocates nothing per tile and never
 * writes to the room's tiles. The estimate is the octile distance, which never overestimates with
 * diagonal steps, and a cheaper route to a queued tile queues it again, so the path is a shortest one.
 * The walking rules are the ones the emulator always had (see {@link #step}).
 *
 * <p>One instance per room; callers serialize searches on it.
 */
final class GridSearch {
    /** Straight steps first, then diagonals, in the order the old search tried them. */
    private static final int[] DX = {-1, 1, 0, 0, 1, -1, -1, 1};

    private static final int[] DY = {0, 0, -1, 1, 1, -1, 1, -1};
    private static final int STEP_EXTRA = DIAGONAL_MOVEMENT_COST - BASIC_MOVEMENT_COST;
    private static final int FIELD_BITS = 21;
    private static final long FIELD_MASK = (1L << FIELD_BITS) - 1;
    private static final int FIELD_MAX = (int) FIELD_MASK;

    /** How the room lets a unit walk. */
    record Rules(
            double maxStepHeight,
            boolean allowFalling,
            boolean diagonals,
            boolean retroDiagonals,
            RoomTile door,
            boolean allowWalkthrough,
            long timeoutNanos) {}

    private int stamp;
    private int[] seen = new int[0];
    private int[] closed = new int[0];
    private int[] cost = new int[0];
    private int[] parent = new int[0];
    private int[] overrideSeen = new int[0];
    private boolean[] overrides = new boolean[0];
    private long[] heap = new long[256];
    private int heapSize;
    private int expanded;

    /**
     * A shortest path from {@code start} to {@code target}: the tiles after the start up to the target,
     * empty when there is none (or the search ran out of time). With {@code stopNextToTarget} the path
     * ends on the first reachable tile next to the target instead (for a target someone stands on).
     */
    Deque<RoomTile> find(
            PathGrid grid,
            Rules rules,
            RoomTile start,
            RoomTile target,
            RoomTile goal,
            boolean walkthroughRetry,
            boolean stopNextToTarget) {
        int width = grid.width();
        int height = grid.height();
        if (!inside(start.x, start.y, width, height) || !inside(target.x, target.y, width, height)) {
            return new ArrayDeque<>();
        }
        this.prepare(width * height);

        long started = rules.timeoutNanos() > 0 ? System.nanoTime() : 0L;
        int startNode = start.y * width + start.x;
        int targetX = target.x;
        int targetY = target.y;
        int moves = rules.diagonals() ? 8 : 4;

        this.cost[startNode] = 0;
        this.seen[startNode] = this.stamp;
        this.push(this.estimate(start.x, start.y, targetX, targetY, rules.diagonals(), stopNextToTarget), 0, startNode);

        while (this.heapSize > 0) {
            if (started != 0L
                    && (++this.expanded & (TIMEOUT_CHECK_INTERVAL - 1)) == 0
                    && System.nanoTime() - started > rules.timeoutNanos()) {
                return new ArrayDeque<>();
            }

            long entry = this.pop();
            int node = (int) (entry & FIELD_MASK);
            if (this.closed[node] == this.stamp) {
                continue;
            }
            int x = node % width;
            int y = node / width;
            int f = (int) (entry >>> (2 * FIELD_BITS));
            if (f > this.cost[node] + this.estimate(x, y, targetX, targetY, rules.diagonals(), stopNextToTarget)) {
                continue; // a cheaper entry for this tile was queued after this one
            }
            if ((x == targetX && y == targetY) || (stopNextToTarget && nextTo(x, y, targetX, targetY, rules))) {
                return this.trace(grid, width, startNode, node);
            }
            this.closed[node] = this.stamp;

            RoomTile current = grid.tile(x, y);
            if (current == null) {
                continue;
            }
            for (int move = 0; move < moves; move++) {
                int nx = x + DX[move];
                int ny = y + DY[move];
                if (!inside(nx, ny, width, height)) {
                    continue;
                }
                int next = ny * width + nx;
                if (this.closed[next] == this.stamp) {
                    continue;
                }
                RoomTile tile = grid.tile(nx, ny);
                if (tile == null) {
                    continue;
                }
                boolean diagonal = move >= 4;
                int verdict = this.step(grid, rules, current, tile, target, goal, diagonal, walkthroughRetry, next);
                if (verdict == CLOSE) {
                    this.closed[next] = this.stamp;
                }
                if (verdict != ENTER) {
                    continue;
                }
                int candidate = this.cost[node] + (diagonal ? DIAGONAL_MOVEMENT_COST : BASIC_MOVEMENT_COST);
                if (this.seen[next] != this.stamp || candidate < this.cost[next]) {
                    this.seen[next] = this.stamp;
                    this.cost[next] = candidate;
                    this.parent[next] = node;
                    int estimate = this.estimate(nx, ny, targetX, targetY, rules.diagonals(), stopNextToTarget);
                    this.push(candidate + estimate, estimate, next);
                }
            }
        }
        return new ArrayDeque<>();
    }

    /** Tiles expanded by the last search (for tests and diagnostics). */
    int expanded() {
        return this.expanded;
    }

    private static final int SKIP = 0;
    private static final int CLOSE = 1;
    private static final int ENTER = 2;

    /**
     * Whether the walker may step from {@code from} onto {@code to}: the rules of the emulator's
     * pathfinder, unchanged. CLOSE marks a tile no other route may enter either (furni, a seat that is
     * not the goal, a unit), SKIP only refuses this step (a too high step from here).
     */
    private int step(
            PathGrid grid,
            Rules rules,
            RoomTile from,
            RoomTile to,
            RoomTile target,
            RoomTile goal,
            boolean diagonal,
            boolean walkthroughRetry,
            int node) {
        if (diagonal) {
            if (!rules.retroDiagonals() && this.cornerBlocked(grid, from.x, from.y, to.x, to.y)) {
                return SKIP;
            }
            if (!to.isWalkable() && !to.equals(goal)) {
                return SKIP;
            }
        }
        if (to.state == RoomTileState.SIT && target.getStackHeight() - from.getStackHeight() > 2.0) {
            return SKIP;
        }

        boolean override = this.override(grid, to, node);
        if (!override && (to.state == RoomTileState.BLOCKED || to.state == RoomTileState.INVALID)) {
            return SKIP;
        }
        if (override) {
            return ENTER;
        }
        if ((to.state == RoomTileState.SIT || to.state == RoomTileState.LAY) && !to.equals(goal)) {
            return CLOSE;
        }

        double rise = to.getStackHeight() - from.getStackHeight();
        if ((!rules.allowFalling() && rise < -rules.maxStepHeight())
                || (to.state == RoomTileState.OPEN && rise > rules.maxStepHeight())) {
            return SKIP;
        }

        if (grid.occupied(to.x, to.y)
                && (rules.door() == null || rules.door().distance(to) > DISTANCE_DOOR_THRESHOLD)
                && (!walkthroughRetry || !rules.allowWalkthrough() || to.equals(goal))) {
            return CLOSE;
        }
        return ENTER;
    }

    /** A diagonal step is refused only when both tiles it passes between are not walkable. */
    private boolean cornerBlocked(PathGrid grid, int x, int y, int nx, int ny) {
        RoomTile alongX = grid.tile(nx, y);
        RoomTile alongY = grid.tile(x, ny);
        return alongX == null || alongY == null || (!alongX.isWalkable() && !alongY.isWalkable());
    }

    private boolean override(PathGrid grid, RoomTile tile, int node) {
        if (this.overrideSeen[node] != this.stamp) {
            this.overrideSeen[node] = this.stamp;
            this.overrides[node] = grid.canOverride(tile);
        }
        return this.overrides[node];
    }

    /**
     * Octile distance: straight steps cost 10, diagonals 11, so it never overestimates. Ending next to
     * the target is up to one (diagonal) step shorter.
     */
    private int estimate(int x, int y, int targetX, int targetY, boolean diagonals, boolean nextToTarget) {
        int dx = Math.abs(x - targetX);
        int dy = Math.abs(y - targetY);
        int distance = diagonals
                ? BASIC_MOVEMENT_COST * Math.max(dx, dy) + STEP_EXTRA * Math.min(dx, dy)
                : BASIC_MOVEMENT_COST * (dx + dy);
        if (nextToTarget) {
            distance = Math.max(0, distance - DIAGONAL_MOVEMENT_COST);
        }
        return distance;
    }

    private static boolean nextTo(int x, int y, int targetX, int targetY, Rules rules) {
        int dx = Math.abs(x - targetX);
        int dy = Math.abs(y - targetY);
        return rules.diagonals() ? Math.max(dx, dy) == 1 : dx + dy == 1;
    }

    private Deque<RoomTile> trace(PathGrid grid, int width, int startNode, int node) {
        Deque<RoomTile> path = new ArrayDeque<>();
        for (int at = node; at != startNode; at = this.parent[at]) {
            path.addFirst(grid.tile(at % width, at / width));
        }
        return path;
    }

    private static boolean inside(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private void prepare(int tiles) {
        if (this.seen.length < tiles) {
            this.seen = new int[tiles];
            this.closed = new int[tiles];
            this.cost = new int[tiles];
            this.parent = new int[tiles];
            this.overrideSeen = new int[tiles];
            this.overrides = new boolean[tiles];
            this.stamp = 0;
        }
        if (++this.stamp == Integer.MAX_VALUE) {
            java.util.Arrays.fill(this.seen, 0);
            java.util.Arrays.fill(this.closed, 0);
            java.util.Arrays.fill(this.overrideSeen, 0);
            this.stamp = 1;
        }
        this.heapSize = 0;
        this.expanded = 0;
    }

    /**
     * Open-list entries are longs ordered by f, then h (nearer the target first), then tile index, so
     * equal routes always come out the same way.
     */
    private void push(int f, int h, int node) {
        long entry = ((long) Math.min(f, FIELD_MAX) << (2 * FIELD_BITS))
                | ((long) Math.min(h, FIELD_MAX) << FIELD_BITS)
                | node;
        if (this.heapSize == this.heap.length) {
            this.heap = java.util.Arrays.copyOf(this.heap, this.heap.length * 2);
        }
        int at = this.heapSize++;
        while (at > 0) {
            int up = (at - 1) >>> 1;
            if (this.heap[up] <= entry) {
                break;
            }
            this.heap[at] = this.heap[up];
            at = up;
        }
        this.heap[at] = entry;
    }

    private long pop() {
        long top = this.heap[0];
        long last = this.heap[--this.heapSize];
        int at = 0;
        int half = this.heapSize >>> 1;
        while (at < half) {
            int child = 2 * at + 1;
            if (child + 1 < this.heapSize && this.heap[child + 1] < this.heap[child]) {
                child++;
            }
            if (last <= this.heap[child]) {
                break;
            }
            this.heap[at] = this.heap[child];
            at = child;
        }
        if (this.heapSize > 0) {
            this.heap[at] = last;
        }
        return top;
    }
}
