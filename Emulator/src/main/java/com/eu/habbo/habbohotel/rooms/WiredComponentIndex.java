package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The wired boxes of one kind in a room (triggers, effects, conditions or extras), found by item id, by their type and
 * by the tile they stand on. {@link RoomSpecialTypes} keeps one of these per kind.
 *
 * <p>What the lookups return is a copy, so a caller may change it and may keep iterating while boxes are added or
 * moved. An index built without a type function files its boxes by id only.
 *
 * @param <K> the type a box is filed under, such as a {@code WiredTriggerType}
 * @param <T> the kind of box
 */
final class WiredComponentIndex<T extends HabboItem, K> {
    private final Function<T, K> typeOf;
    private final ConcurrentHashMap<Integer, T> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Set<T>> byType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<T>> byTile = new ConcurrentHashMap<>();

    WiredComponentIndex(Function<T, K> typeOf) {
        this.typeOf = typeOf;
    }

    /** An index that files its boxes by id and tile only. */
    static <T extends HabboItem> WiredComponentIndex<T, Void> byTileOnly() {
        return new WiredComponentIndex<>(null);
    }

    /** Files two shorts as one long key. */
    private static long tileKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    void add(T box) {
        this.byId.put(box.getId(), box);
        if (this.typeOf != null) {
            this.byType
                    .computeIfAbsent(this.typeOf.apply(box), type -> ConcurrentHashMap.newKeySet())
                    .add(box);
        }
        this.byTile
                .computeIfAbsent(tileKey(box.getX(), box.getY()), key -> ConcurrentHashMap.newKeySet())
                .add(box);
    }

    void remove(T box) {
        this.byId.remove(box.getId());
        if (this.typeOf != null) {
            K type = this.typeOf.apply(box);
            Set<T> ofType = this.byType.get(type);
            if (ofType != null) {
                ofType.remove(box);
                if (ofType.isEmpty()) {
                    this.byType.remove(type);
                }
            }
        }
        this.removeFromTile(box, tileKey(box.getX(), box.getY()));
    }

    /** Re-files a box that moved: it was on {@code oldX},{@code oldY} and now stands where it says. */
    void moved(T box, int oldX, int oldY) {
        this.removeFromTile(box, tileKey(oldX, oldY));
        this.byTile
                .computeIfAbsent(tileKey(box.getX(), box.getY()), key -> ConcurrentHashMap.newKeySet())
                .add(box);
    }

    T find(int itemId) {
        return this.byId.get(itemId);
    }

    Set<T> all() {
        if (this.typeOf == null) {
            return new LinkedHashSet<>(this.byId.values());
        }
        Set<T> result = new LinkedHashSet<>();
        for (Set<T> ofType : this.byType.values()) {
            result.addAll(ofType);
        }
        return result;
    }

    Set<T> ofType(K type) {
        Set<T> ofType = this.byType.get(type);
        return (ofType == null) ? new LinkedHashSet<>(0) : new LinkedHashSet<>(ofType);
    }

    Set<T> at(int x, int y) {
        Set<T> onTile = this.byTile.get(tileKey(x, y));
        return (onTile == null) ? new LinkedHashSet<>(0) : new LinkedHashSet<>(onTile);
    }

    /** The boxes of one type as they are filed, not a copy; null when there are none. For callers that only read. */
    Set<T> liveOfType(K type) {
        return this.byType.get(type);
    }

    /** The live set of boxes on a tile, not a copy; null when there are none. For callers that only read. */
    Set<T> liveAt(int x, int y) {
        return this.byTile.get(tileKey(x, y));
    }

    void clear() {
        this.byId.clear();
        this.byType.clear();
        this.byTile.clear();
    }

    private void removeFromTile(T box, long key) {
        Set<T> onTile = this.byTile.get(key);
        if (onTile != null) {
            onTile.remove(box);
            if (onTile.isEmpty()) {
                this.byTile.remove(key);
            }
        }
    }
}
