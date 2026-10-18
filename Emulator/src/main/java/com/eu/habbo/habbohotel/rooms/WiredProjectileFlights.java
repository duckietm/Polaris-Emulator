package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredProjectileFlight;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The last flight of every furni a projectile add-on launched in one room. A furni keeps its flight
 * after it lands, so a stack can read how far the shot went; it is dropped when the furni leaves the
 * room or the room unloads, and the oldest flights go first past {@link #MAX_FLIGHTS}.
 */
public final class WiredProjectileFlights {
    public static final int MAX_FLIGHTS = 1_024;

    private final LongSupplier clock;
    private final Map<Integer, WiredProjectileFlight> flights = new LinkedHashMap<>();
    /** Flights still in the air: the counts last reported for the {@link #WATCHED} variables. */
    private final Map<Integer, Integer[]> watched = new LinkedHashMap<>();

    private boolean watcherClaimed;

    /**
     * The projectile variables Variable Changed can watch, as in Habbo: a change is reported on the room tick
     * after the projectile crosses into a tile, never at launch, and the traveling flag goes when it lands.
     */
    public static final List<String> WATCHED = List.of(
            WiredProjectileFlight.TILES_TRAVELED,
            WiredProjectileFlight.USER_COLLISIONS,
            WiredProjectileFlight.FURNI_COLLISIONS,
            WiredProjectileFlight.IS_TRAVELING);

    /** One watched variable that moved: null stands for "not held" (the traveling flag after landing). */
    public record Change(int itemId, String key, Integer before, Integer after) {}

    public WiredProjectileFlights() {
        this(System::currentTimeMillis);
    }

    WiredProjectileFlights(LongSupplier clock) {
        this.clock = clock;
    }

    public long now() {
        return this.clock.getAsLong();
    }

    public synchronized void begin(int itemId, WiredProjectileFlight flight) {
        if (flight == null) {
            return;
        }

        this.flights.remove(itemId);
        this.flights.put(itemId, flight);
        this.watched.put(itemId, watchedValues(flight, this.now()));

        while (this.flights.size() > MAX_FLIGHTS) {
            Integer eldest = this.flights.keySet().iterator().next();
            this.flights.remove(eldest);
            this.watched.remove(eldest);
        }
    }

    /** Whether a flight is still watched for changes. */
    public synchronized boolean hasPending() {
        return !this.watched.isEmpty();
    }

    /**
     * The watched variables that moved since the last poll; a flight stops being watched once it has
     * landed (after its landing changes are reported).
     */
    public synchronized List<Change> poll(long nowMs) {
        if (this.watched.isEmpty()) {
            return Collections.emptyList();
        }

        List<Change> changes = new ArrayList<>();
        Iterator<Map.Entry<Integer, Integer[]>> entries =
                this.watched.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, Integer[]> entry = entries.next();
            WiredProjectileFlight flight = this.flights.get(entry.getKey());
            if (flight == null) {
                entries.remove();
                continue;
            }

            Integer[] last = entry.getValue();
            Integer[] now = watchedValues(flight, nowMs);
            for (int i = 0; i < now.length; i++) {
                if (!java.util.Objects.equals(last[i], now[i])) {
                    changes.add(new Change(entry.getKey(), WATCHED.get(i), last[i], now[i]));
                }
            }
            entry.setValue(now);

            if (!flight.isTraveling(nowMs)) {
                entries.remove();
            }
        }
        return changes;
    }

    /**
     * Claims the room's watcher for a new flight: true when nobody watches the flights yet, so the
     * caller starts one.
     */
    public synchronized boolean claimWatcher() {
        if (this.watcherClaimed) {
            return false;
        }
        this.watcherClaimed = true;
        return true;
    }

    /** The watcher lets go when no flight is watched any more; false while one still is. */
    public synchronized boolean releaseWatcherIfIdle() {
        if (!this.watched.isEmpty()) {
            return false;
        }
        this.watcherClaimed = false;
        return true;
    }

    private static Integer[] watchedValues(WiredProjectileFlight flight, long nowMs) {
        Integer[] values = new Integer[WATCHED.size()];
        for (int i = 0; i < values.length; i++) {
            String key = WATCHED.get(i);
            // The traveling flag has no value: it reads 1 while held.
            values[i] = WiredProjectileFlight.IS_TRAVELING.equals(key)
                    ? (flight.holds(key, nowMs) ? Integer.valueOf(1) : null)
                    : flight.read(key, nowMs);
        }
        return values;
    }

    public synchronized WiredProjectileFlight get(int itemId) {
        return this.flights.get(itemId);
    }

    public boolean holds(HabboItem item, String key) {
        WiredProjectileFlight flight = (item == null) ? null : this.get(item.getId());
        return flight != null && flight.holds(key, this.now());
    }

    public Integer read(HabboItem item, String key) {
        WiredProjectileFlight flight = (item == null) ? null : this.get(item.getId());
        return (flight == null) ? null : flight.read(key, this.now());
    }

    public synchronized int size() {
        return this.flights.size();
    }

    synchronized void forget(int itemId) {
        this.flights.remove(itemId);
        this.watched.remove(itemId);
    }

    synchronized void clear() {
        this.flights.clear();
        this.watched.clear();
        this.watcherClaimed = false;
    }
}
