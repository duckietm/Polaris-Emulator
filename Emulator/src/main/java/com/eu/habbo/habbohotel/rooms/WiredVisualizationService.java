package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Room-owned bounded state for the official wired visualizations the client draws itself: furni and
 * avatar highlights, and avatar transparency. Each is kept room-wide (the audience was everyone) and
 * per viewer (only the picked users see it); a viewer's own state wins over the room-wide one. Furni
 * are keyed by item id, avatars by room unit index and viewers by user id.
 */
final class WiredVisualizationService {
    /** The viewer id of state everyone in the room sees. */
    static final int EVERYONE = 0;

    static final int DEFAULT_MAXIMUM_STATES = 5_000;
    static final int ABSOLUTE_MAXIMUM_STATES = 20_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVisualizationService.class);

    private final Room room;
    private final int maximumStates;
    private final Layer<WiredHighlight> furniHighlights = new Layer<>(WiredHighlight.NONE);
    private final Layer<WiredHighlight> userHighlights = new Layer<>(WiredHighlight.NONE);
    private final Layer<WiredUserTransparency> userTransparency = new Layer<>(WiredUserTransparency.NONE);
    private int stateCount;
    private boolean disposed;

    WiredVisualizationService(Room room) {
        this(room, configuredMaximumStates());
    }

    WiredVisualizationService(Room room, int maximumStates) {
        this.room = room;
        this.maximumStates = Math.max(1, Math.min(ABSOLUTE_MAXIMUM_STATES, maximumStates));
    }

    /** Highlights the items for the viewer (or {@link #EVERYONE}); returns the ids of the items it took. */
    synchronized List<Integer> highlightFurni(int viewerId, Collection<HabboItem> items, WiredHighlight highlight) {
        if (this.disposed || viewerId < 0 || highlight == null || items == null) {
            return List.of();
        }
        List<Integer> itemIds = items.stream()
                .filter(this::isCurrentRoomItem)
                .map(HabboItem::getId)
                .distinct()
                .sorted()
                .toList();
        return this.furniHighlights.apply(viewerId, itemIds, highlight, highlight.isCleared());
    }

    /** Highlights the avatars for the viewer (or {@link #EVERYONE}); returns the room unit indices it took. */
    synchronized List<Integer> highlightUsers(int viewerId, Collection<RoomUnit> units, WiredHighlight highlight) {
        if (this.disposed || viewerId < 0 || highlight == null) {
            return List.of();
        }
        return this.userHighlights.apply(viewerId, currentUnits(units), highlight, highlight.isCleared());
    }

    /**
     * Sets the avatars' transparency for the viewer (or {@link #EVERYONE}); returns the room unit indices
     * it took. A viewer's "fully visible" is kept, so it can still override a room-wide transparency.
     */
    synchronized List<Integer> setUserTransparency(
            int viewerId, Collection<RoomUnit> units, WiredUserTransparency transparency) {
        if (this.disposed || viewerId < 0 || transparency == null) {
            return List.of();
        }
        boolean clear = viewerId == EVERYONE && transparency.isDefault();
        return this.userTransparency.apply(viewerId, currentUnits(units), transparency, clear);
    }

    /** What the viewer sees on these items, wall items with their id negated. */
    synchronized List<WiredVisualizationEntry<WiredHighlight>> effectiveFurniHighlights(
            int viewerId, Collection<Integer> itemIds) {
        if (this.disposed || itemIds == null) {
            return List.of();
        }
        ArrayList<WiredVisualizationEntry<WiredHighlight>> result = new ArrayList<>();
        for (Integer itemId : new TreeSet<>(itemIds)) {
            HabboItem item = itemId == null ? null : this.room.getHabboItem(itemId);
            if (isCurrentRoomItem(item)) {
                result.add(
                        new WiredVisualizationEntry<>(wireId(item), this.furniHighlights.effective(viewerId, itemId)));
            }
        }
        return List.copyOf(result);
    }

    synchronized List<WiredVisualizationEntry<WiredHighlight>> effectiveUserHighlights(
            int viewerId, Collection<Integer> roomUnitIds) {
        return this.disposed ? List.of() : this.userHighlights.effectiveAll(viewerId, roomUnitIds);
    }

    synchronized List<WiredVisualizationEntry<WiredUserTransparency>> effectiveUserTransparency(
            int viewerId, Collection<Integer> roomUnitIds) {
        return this.disposed ? List.of() : this.userTransparency.effectiveAll(viewerId, roomUnitIds);
    }

    /** Every furni highlight the user sees on entering: the room-wide ones and their own. */
    synchronized List<WiredVisualizationEntry<WiredHighlight>> furniHighlightSnapshot(int userId) {
        return effectiveFurniHighlights(userId, this.furniHighlights.keysFor(userId));
    }

    synchronized List<WiredVisualizationEntry<WiredHighlight>> userHighlightSnapshot(int userId) {
        return effectiveUserHighlights(userId, this.userHighlights.keysFor(userId));
    }

    synchronized List<WiredVisualizationEntry<WiredUserTransparency>> userTransparencySnapshot(int userId) {
        return effectiveUserTransparency(userId, this.userTransparency.keysFor(userId));
    }

    synchronized void forgetItem(HabboItem item) {
        if (item != null) {
            this.furniHighlights.forgetKey(item.getId());
        }
    }

    /** The user left: drop what was shown on their avatar and what only they were shown. */
    synchronized void forgetUser(int userId, int roomUnitId) {
        if (userId > 0) {
            this.furniHighlights.forgetViewer(userId);
            this.userHighlights.forgetViewer(userId);
            this.userTransparency.forgetViewer(userId);
        }
        this.userHighlights.forgetKey(roomUnitId);
        this.userTransparency.forgetKey(roomUnitId);
    }

    synchronized void dispose() {
        this.disposed = true;
        this.furniHighlights.clear();
        this.userHighlights.clear();
        this.userTransparency.clear();
        this.stateCount = 0;
    }

    synchronized int stateCount() {
        return this.stateCount;
    }

    private List<Integer> currentUnits(Collection<RoomUnit> units) {
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        return units.stream()
                .filter(unit -> unit != null && this.room.getHabbo(unit) != null)
                .map(RoomUnit::getId)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean isCurrentRoomItem(HabboItem item) {
        return item != null
                && item.getId() > 0
                && item.getRoomId() == this.room.getId()
                && item.getBaseItem() != null
                && this.room.getHabboItem(item.getId()) == item;
    }

    private static int wireId(HabboItem item) {
        return item.getBaseItem().getType() == FurnitureType.WALL ? -item.getId() : item.getId();
    }

    private boolean reserveState(int key) {
        if (this.stateCount >= this.maximumStates) {
            LOGGER.warn(
                    "Visualization state limit reached: room={}, configuredLimit={}, rejectedTarget={}",
                    this.room.getId(),
                    this.maximumStates,
                    key);
            return false;
        }
        this.stateCount++;
        return true;
    }

    private static int configuredMaximumStates() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        if (configuration == null) {
            return DEFAULT_MAXIMUM_STATES;
        }
        return Math.max(
                1,
                Math.min(
                        ABSOLUTE_MAXIMUM_STATES,
                        configuration.getInt("wired.visualization.max_states_per_room", DEFAULT_MAXIMUM_STATES)));
    }

    /** One kind of state: room-wide values and per-viewer overlays, sharing the room's state budget. */
    private final class Layer<V> {
        private final V none;
        private final Map<Integer, V> global = new HashMap<>();
        private final Map<Integer, Map<Integer, V>> viewers = new HashMap<>();

        private Layer(V none) {
            this.none = none;
        }

        private List<Integer> apply(int viewerId, List<Integer> keys, V value, boolean clear) {
            Map<Integer, V> states = viewerId == EVERYONE
                    ? this.global
                    : this.viewers.computeIfAbsent(viewerId, ignored -> new HashMap<>());
            ArrayList<Integer> applied = new ArrayList<>();
            for (Integer key : keys) {
                if (clear) {
                    if (states.remove(key) != null) {
                        WiredVisualizationService.this.stateCount--;
                    }
                    applied.add(key);
                } else if (states.containsKey(key) || reserveState(key)) {
                    states.put(key, value);
                    applied.add(key);
                }
            }
            if (viewerId != EVERYONE && states.isEmpty()) {
                this.viewers.remove(viewerId);
            }
            return List.copyOf(applied);
        }

        private V effective(int viewerId, int key) {
            Map<Integer, V> personal = this.viewers.get(viewerId);
            V value = personal == null ? null : personal.get(key);
            return value != null ? value : this.global.getOrDefault(key, this.none);
        }

        private List<WiredVisualizationEntry<V>> effectiveAll(int viewerId, Collection<Integer> keys) {
            if (keys == null) {
                return List.of();
            }
            ArrayList<WiredVisualizationEntry<V>> result = new ArrayList<>();
            for (Integer key : new TreeSet<>(keys)) {
                if (key != null) {
                    result.add(new WiredVisualizationEntry<>(key, effective(viewerId, key)));
                }
            }
            return List.copyOf(result);
        }

        private TreeSet<Integer> keysFor(int viewerId) {
            TreeSet<Integer> keys = new TreeSet<>(this.global.keySet());
            Map<Integer, V> personal = this.viewers.get(viewerId);
            if (personal != null) {
                keys.addAll(personal.keySet());
            }
            return keys;
        }

        private void forgetKey(int key) {
            if (this.global.remove(key) != null) {
                WiredVisualizationService.this.stateCount--;
            }
            for (Map<Integer, V> states : this.viewers.values()) {
                if (states.remove(key) != null) {
                    WiredVisualizationService.this.stateCount--;
                }
            }
            this.viewers.values().removeIf(Map::isEmpty);
        }

        private void forgetViewer(int viewerId) {
            Map<Integer, V> removed = this.viewers.remove(viewerId);
            if (removed != null) {
                WiredVisualizationService.this.stateCount -= removed.size();
            }
        }

        private void clear() {
            this.global.clear();
            this.viewers.clear();
        }
    }
}
