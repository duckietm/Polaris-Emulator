package polaris.hotel.rooms.pathfinding.impl;

import static polaris.hotel.rooms.pathfinding.impl.PathfinderConstants.CONFIG_EXECUTION_TIME;
import static polaris.hotel.rooms.pathfinding.impl.PathfinderConstants.CONFIG_OCCUPIED_GOAL_WALK_ADJACENT;
import static polaris.hotel.rooms.pathfinding.impl.PathfinderConstants.CONFIG_TIMEOUT_ENABLED;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.interactions.InteractionObstacle;
import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.pathfinding.Pathfinder;
import polaris.hotel.users.User;

public class PathfinderImpl implements Pathfinder {

    private static final int CACHED_TIMEOUT_MS = Emulator.getConfig().getInt(CONFIG_EXECUTION_TIME, 25);
    // Default ON: bound A* to CACHED_TIMEOUT_MS (25ms) so a pathological search
    // can't run unbounded and stall the thread. On timeout findPath returns an
    // empty path (the unit simply doesn't move there) — graceful degradation.
    private static final boolean CACHED_TIMEOUT_ENABLED = Emulator.getConfig().getBoolean(CONFIG_TIMEOUT_ENABLED, true);
    private static final long CACHED_TIMEOUT_NANOS = CACHED_TIMEOUT_MS * 1_000_000L;

    private final Room room;
    private double maximumStepHeight;
    private boolean allowFalling;
    private final boolean retroStyleDiagonals;
    /** Search arrays of this room, reused; searches of one room take turns on it. */
    private final GridSearch search = new GridSearch();

    public PathfinderImpl(Room room, double maximumStepHeight, boolean allowFalling, boolean retroStyleDiagonals) {
        this.room = room;
        this.maximumStepHeight = maximumStepHeight;
        this.allowFalling = allowFalling;
        this.retroStyleDiagonals = retroStyleDiagonals;
    }

    @Override
    public CompletableFuture<Deque<RoomTile>> findPathAsync(
            RoomTile oldTile, RoomTile newTile, RoomTile goalLocation, RoomUnit roomUnit) {
        return CompletableFuture.supplyAsync(() -> findPath(oldTile, newTile, goalLocation, roomUnit))
                .exceptionally(error -> {
                    throw new RuntimeException(new PathFinderException("Failed to find path", error));
                });
    }

    @Override
    public Deque<RoomTile> findPath(RoomTile oldTile, RoomTile newTile, RoomTile goalLocation, RoomUnit roomUnit) {
        return this.findPath(oldTile, newTile, goalLocation, roomUnit, false);
    }

    /**
     * A shortest path. In a walkthrough room avatars are no obstacle at all: the path goes straight through them
     * and may end on a tile someone stands on, as in Habbo. Otherwise someone standing blocks; when they stand on
     * the goal, the walker comes as close as the tile next to it (config
     * {@code pathfinder.occupied_goal.walk_adjacent}, default on).
     */
    @Override
    public Deque<RoomTile> findPath(
            RoomTile oldTile, RoomTile newTile, RoomTile goalLocation, RoomUnit roomUnit, boolean isWalkthroughRetry) {
        RoomLayout layout = this.room == null ? null : this.room.getLayout();
        if (layout == null
                || !this.room.isLoaded()
                || oldTile == null
                || newTile == null
                || roomUnit == null
                || oldTile.equals(newTile)
                || newTile.getState() == RoomTileState.INVALID) {
            return new ArrayDeque<>();
        }

        GridSearch.Rules rules = new GridSearch.Rules(
                this.maximumStepHeight,
                this.allowFalling,
                this.room.moveDiagonally(),
                this.retroStyleDiagonals,
                layout.getDoorTile(),
                this.room.isAllowWalkthrough(),
                CACHED_TIMEOUT_ENABLED ? CACHED_TIMEOUT_NANOS : 0L);
        RoomPathGrid grid = new RoomPathGrid(this.room, layout, roomUnit);

        synchronized (this.search) {
            boolean throughUnits = rules.allowWalkthrough() || isWalkthroughRetry;
            Deque<RoomTile> path = this.search.find(grid, rules, oldTile, newTile, goalLocation, throughUnits, false);
            if (path.isEmpty()
                    && !rules.allowWalkthrough()
                    && grid.occupied(newTile.x, newTile.y)
                    && walkNextToOccupiedGoal()) {
                path = this.search.find(grid, rules, oldTile, newTile, goalLocation, isWalkthroughRetry, true);
            }
            return path;
        }
    }

    private static boolean walkNextToOccupiedGoal() {
        ConfigurationManager config = WiredPlatform.configuration();
        return config == null || config.getBoolean(CONFIG_OCCUPIED_GOAL_WALK_ADJACENT, true);
    }

    /** The room as the search sees it: its tiles, and who stands where at the start of the search. */
    private static final class RoomPathGrid implements PathGrid {
        private final RoomLayout layout;
        private final RoomUnit walker;
        private final int width;
        private final int height;
        private final long[] occupied;
        private final boolean mayOverride;

        RoomPathGrid(Room room, RoomLayout layout, RoomUnit walker) {
            this.layout = layout;
            this.walker = walker;
            this.width = layout.getMapSizeX();
            this.height = layout.getMapSizeY();
            this.occupied = new long[(this.width * this.height + 63) >>> 6];

            // A rider walks over the pet it sits on.
            User user = room.getUser(walker);
            RoomUnit mount = user != null
                            && user.getUserInfo() != null
                            && user.getUserInfo().getRiding() != null
                    ? user.getUserInfo().getRiding().getRoomUnit()
                    : null;
            for (User other : room.getCurrentUsers().values()) {
                this.markOccupied(other.getRoomUnit(), walker, mount);
            }
            for (Pet pet : room.getCurrentPets().values()) {
                this.markOccupied(pet.getRoomUnit(), walker, mount);
            }
            for (Bot bot : room.getCurrentBots().values()) {
                this.markOccupied(bot.getRoomUnit(), walker, mount);
            }

            // Only these furni (and tiles granted to the walker) are walkable whatever their state;
            // without them no tile needs the walker's per-tile furni check.
            RoomSpecialTypes special = room.getRoomSpecialTypes();
            this.mayOverride = walker.hasOverrideTiles()
                    || special == null
                    || !special.getItemsOfType(InteractionObstacle.class).isEmpty()
                    || !special.getItemsOfType(InteractionPetBreedingNest.class).isEmpty();
        }

        private void markOccupied(RoomUnit unit, RoomUnit walker, RoomUnit mount) {
            RoomTile at = unit == null ? null : unit.getCurrentLocation();
            if (unit == walker
                    || unit == mount
                    || at == null
                    || at.x < 0
                    || at.y < 0
                    || at.x >= this.width
                    || at.y >= this.height) {
                return;
            }
            int index = at.y * this.width + at.x;
            this.occupied[index >>> 6] |= 1L << (index & 63);
        }

        @Override
        public int width() {
            return this.width;
        }

        @Override
        public int height() {
            return this.height;
        }

        @Override
        public RoomTile tile(int x, int y) {
            return this.layout.getTile((short) x, (short) y);
        }

        @Override
        public boolean occupied(int x, int y) {
            if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
                return false;
            }
            int index = y * this.width + x;
            return (this.occupied[index >>> 6] & (1L << (index & 63))) != 0;
        }

        @Override
        public boolean canOverride(RoomTile tile) {
            return this.mayOverride && this.walker.canOverrideTile(tile);
        }
    }

    /**
     * @deprecated the path comes from {@link #findPath}; kept for plugins built against earlier releases.
     */
    @Deprecated
    public Deque<RoomTile> tracePath(RoomTile start, RoomTile goal) {
        Deque<RoomTile> path = new ArrayDeque<>();
        RoomLayout layout = this.room == null ? null : this.room.getLayout();
        if (start == null || layout == null) {
            return path;
        }
        RoomTile current = goal;
        while (current != null) {
            path.addFirst(layout.getTile(current.getX(), current.getY()));
            current = current.getPrevious();
            if (current != null && current.equals(start)) {
                return path;
            }
        }
        return path;
    }

    @Override
    public boolean isAllowFalling() {
        return this.allowFalling;
    }

    @Override
    public void setAllowFalling(boolean allow) {
        this.allowFalling = allow;
    }

    @Override
    public double getMaxStepHeight() {
        return this.maximumStepHeight;
    }

    @Override
    public void setMaxStepHeight(double value) {
        this.maximumStepHeight = value;
    }
}
