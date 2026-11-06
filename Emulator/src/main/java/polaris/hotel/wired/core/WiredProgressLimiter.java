package polaris.hotel.wired.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * How much hotel-wide progress (achievements, reward tracks) wired may still hand one user for one
 * key inside a time window. Shared by every room, so moving the stack to another room does not
 * reset it. Fails closed: when the table is full of live windows nothing more is granted.
 */
public final class WiredProgressLimiter {
    public static final int MAX_TRACKED_WINDOWS = 100_000;

    private final LongSupplier clock;
    private final int maxTrackedWindows;
    private final Map<String, Window> windows = new HashMap<>();

    public WiredProgressLimiter() {
        this(System::currentTimeMillis, MAX_TRACKED_WINDOWS);
    }

    WiredProgressLimiter(LongSupplier clock, int maxTrackedWindows) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxTrackedWindows = Math.max(1, maxTrackedWindows);
    }

    /**
     * Takes up to {@code requested} units from the user's allowance for {@code key} and returns how
     * many were granted, between 0 and {@code requested}.
     */
    public synchronized int acquire(int userId, String key, int requested, int maxPerWindow, long windowMs) {
        if (userId <= 0 || key == null || requested <= 0 || maxPerWindow <= 0 || windowMs <= 0) {
            return 0;
        }

        long now = this.clock.getAsLong();
        String id = userId + "\t" + key;
        Window window = this.windows.get(id);

        if (window == null || now >= window.expiresAt) {
            if (window == null && this.windows.size() >= this.maxTrackedWindows) {
                this.prune(now);
                if (this.windows.size() >= this.maxTrackedWindows) {
                    return 0;
                }
            }
            window = new Window(now + windowMs);
            this.windows.put(id, window);
        }

        int granted = Math.min(requested, maxPerWindow - window.used);
        if (granted <= 0) {
            return 0;
        }

        window.used += granted;
        return granted;
    }

    public synchronized int trackedWindows() {
        return this.windows.size();
    }

    public synchronized void clear() {
        this.windows.clear();
    }

    private void prune(long now) {
        Iterator<Window> iterator = this.windows.values().iterator();
        while (iterator.hasNext()) {
            if (now >= iterator.next().expiresAt) {
                iterator.remove();
            }
        }
    }

    private static final class Window {
        private final long expiresAt;
        private int used;

        private Window(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
