package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredManager;

/**
 * Runnable task that resets all wired timers in a room.
 * <p>
 * Uses the new centralized WiredTickService for timer management.
 * </p>
 */
public class WiredResetTimers implements Runnable {
    private final Room room;

    public WiredResetTimers(Room room) {
        this.room = room;
    }

    @Override
    public void run() {
        if (!Emulator.isShuttingDown && Emulator.isReady) {
            if (this.room != null && this.room.isLoaded()) {
                try {
                    WiredManager.resetTimers(this.room);
                } catch (Exception e) {
                    // Prevent task from crashing the thread pool
                }
            }
        }
    }
}
