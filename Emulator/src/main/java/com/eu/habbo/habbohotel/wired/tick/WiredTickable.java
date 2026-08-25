package com.eu.habbo.habbohotel.wired.tick;

import com.eu.habbo.habbohotel.rooms.Room;

/**
 * Interface for wired items that need to participate in the 50ms tick system.
 * <p>
 * This replaces the old {@link com.eu.habbo.habbohotel.items.ICycleable} interface
 * for wired items, providing higher-resolution timing (50ms vs 500ms) and 
 * centralized management.
 * </p>
 * 
 * <h3>Lifecycle:</h3>
 * <ol>
 *   <li>Item is placed in room or room is loaded</li>
 *   <li>{@link WiredTickService#register(Room, WiredTickable)} is called</li>
 *   <li>{@link #onWiredTick(Room, long)} is called every 50ms while registered</li>
 *   <li>Item is picked up or room unloaded</li>
 *   <li>{@link WiredTickService#unregister(Room, WiredTickable)} is called</li>
 * </ol>
 * 
 * @see WiredTickService
 */
public interface WiredTickable {
    
    /**
     * Called every tick by the WiredTickService.
     * <p>
     * Implementations should check if they should fire based on the global tick count.
     * For repeaters: fire when (tickCount * tickIntervalMs) % repeatTime == 0
     * </p>
     * 
     * @param room the room this item is in
     * @param tickCount the global tick counter (increments each tick)
     * @param tickIntervalMs the tick interval in milliseconds (e.g., 50)
     */
    void onWiredTick(Room room, long tickCount, int tickIntervalMs);
    
    /**
     * Called when the timer should be reset.
     * <p>
     * This resets any internal counters so the timer starts fresh.
     * Used when game is reset or user resets timers.
     * </p>
     */
    void resetTimer();
    
    /**
     * Gets the unique identifier for this tickable item.
     * <p>
     * Used for efficient registration/unregistration.
     * </p>
     * 
     * @return the item ID
     */
    int getId();
    
    /**
     * Gets the room ID this item belongs to.
     * 
     * @return the room ID
     */
    int getRoomId();
    
    /**
     * Checks if this is a one-shot timer (triggers once then stops)
     * or a repeating timer.
     * 
     * @return true if this timer only fires once per activation
     */
    default boolean isOneShot() {
        return false;
    }
    
    /**
     * Called when the item is first registered with the tick service.
     * <p>
     * Can be used to initialize timing state.
     * </p>
     * 
     * @param room the room
     * @param currentTimeMillis current system time
     */
    default void onRegistered(Room room, long currentTimeMillis) {
        // Default: no-op
    }
    
    /**
     * Called when the item is unregistered from the tick service.
     * <p>
     * Can be used to cleanup timing state.
     * </p>
     * 
     * @param room the room
     */
    default void onUnregistered(Room room) {
        // Default: no-op
    }
}
