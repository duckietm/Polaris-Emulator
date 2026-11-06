package polaris.hotel.rooms.pathfinding;

import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;

/**
 * The Pathfinder interface defines the contract for any class that will implement pathfinding logic.
 */
public interface Pathfinder {

    /**
     * Asynchronously finds a path from the old tile to the new tile.
     *
     * @param oldTile      The starting tile.
     * @param newTile      The destination tile.
     * @param goalLocation The goal location tile.
     * @param roomUnit     The room unit for which the path is being found.
     * @return A deque of RoomTile objects representing the path from the old tile to the new tile.
     */
    CompletableFuture<Deque<RoomTile>> findPathAsync(
            RoomTile oldTile, RoomTile newTile, RoomTile goalLocation, RoomUnit roomUnit);

    /**
     * Finds a path from the old tile to the new tile.
     *
     * @param oldTile      The starting tile.
     * @param newTile      The destination tile.
     * @param goalLocation The goal location tile.
     * @param roomUnit     The room unit for which the path is being found.
     * @return A deque of RoomTile objects representing the path from the old tile to the new tile.
     */
    Deque<RoomTile> findPath(RoomTile oldTile, RoomTile newTile, RoomTile goalLocation, RoomUnit roomUnit);

    /**
     * Finds a path from the old tile to the new tile, with an option to retry if the first attempt fails.
     *
     * @param oldTile            The starting tile.
     * @param newTile            The destination tile.
     * @param goalLocation       The goal location tile.
     * @param roomUnit           The room unit for which the path is being found.
     * @param isWalkthroughRetry If true, the method will retry finding a path if the first attempt fails.
     * @return A deque of RoomTile objects representing the path from the old tile to the new tile.
     */
    Deque<RoomTile> findPath(
            RoomTile oldTile, RoomTile newTile, RoomTile goalLocation, RoomUnit roomUnit, boolean isWalkthroughRetry);

    /**
     * Checks if falling is allowed.
     *
     * @return True if falling is allowed, false otherwise.
     */
    boolean isAllowFalling();

    /**
     * Sets the falling allowance.
     *
     * @param allow If true, falling is allowed.
     */
    void setAllowFalling(boolean allow);

    /**
     * Gets the maximum step height.
     *
     * @return The maximum step height.
     */
    double getMaxStepHeight();

    /**
     * Sets the maximum step height.
     *
     * @param value The value to set as the maximum step height.
     */
    void setMaxStepHeight(double value);
}
