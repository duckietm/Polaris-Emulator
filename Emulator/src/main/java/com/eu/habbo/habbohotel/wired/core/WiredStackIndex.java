package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.wired.api.WiredStack;

import java.util.List;

/**
 * Interface for indexing and retrieving wired stacks by event type.
 * <p>
 * The index provides fast lookup of wired stacks that listen to a specific event type,
 * avoiding the need to scan all wired items in a room on every event.
 * </p>
 * 
 * <h3>Implementation Notes:</h3>
 * <ul>
 *   <li>Index should be rebuilt when wired items are added/removed/moved</li>
 *   <li>Index can be invalidated per-tile for granular updates</li>
 *   <li>Implementations should be thread-safe if used concurrently</li>
 * </ul>
 * 
 * @see WiredStack
 * @see WiredEngine
 */
public interface WiredStackIndex {

    /**
     * Get all wired stacks in a room that listen to the specified event type.
     * 
     * @param room the room to search
     * @param type the event type to match
     * @return list of matching stacks (may be empty, never null)
     */
    List<WiredStack> getStacks(Room room, WiredEvent.Type type);

    /**
     * Rebuild the entire index for a room.
     * Call this when the room is loaded or when major changes occur.
     * 
     * @param room the room to rebuild index for
     */
    default void rebuild(Room room) {
        // Default no-op for on-demand implementations
    }

    /**
     * Invalidate the index for a specific tile.
     * Call this when a wired item is added, removed, or moved on that tile.
     * 
     * @param room the room
     * @param tile the tile that changed
     */
    default void invalidate(Room room, RoomTile tile) {
        // Default no-op for on-demand implementations
    }

    /**
     * Invalidate the entire index for a room.
     * Call this when the room is unloaded or major changes occur.
     * 
     * @param room the room to invalidate
     */
    default void invalidateAll(Room room) {
        // Default no-op for on-demand implementations
    }

    /**
     * Check if the index has cached data for a room.
     * 
     * @param room the room to check
     * @return true if cached data exists
     */
    default boolean isCached(Room room) {
        return false;
    }
}
