package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Room-owned bounded state for global and per-user furniture opacity. */
final class WiredOpacityService {
    static final int DEFAULT_MAXIMUM_STATES = 5_000;
    static final int ABSOLUTE_MAXIMUM_STATES = 20_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredOpacityService.class);

    private final Room room;
    private final int maximumStates;
    private final Map<Integer, VisualState> globalStates = new HashMap<>();
    private final Map<Integer, Map<Integer, VisualState>> userStates = new HashMap<>();
    private int stateCount;
    private boolean disposed;

    WiredOpacityService(Room room) {
        this(room, configuredMaximumStates());
    }

    WiredOpacityService(Room room, int maximumStates) {
        this.room = room;
        this.maximumStates = Math.max(1, Math.min(ABSOLUTE_MAXIMUM_STATES, maximumStates));
    }

    synchronized List<WiredOpacityState> applyGlobal(Collection<HabboItem> items, int opacity, boolean clickThrough) {
        if (this.disposed) {
            return List.of();
        }

        VisualState next = new VisualState(opacity, clickThrough);
        ArrayList<WiredOpacityState> applied = new ArrayList<>();
        for (HabboItem item : validItems(items)) {
            VisualState previous = this.globalStates.get(item.getId());
            if (next.isDefault()) {
                if (previous != null) {
                    this.globalStates.remove(item.getId());
                    this.stateCount--;
                }
                applied.add(state(item, next));
            } else if (previous != null || reserveState(item.getId())) {
                this.globalStates.put(item.getId(), next);
                applied.add(state(item, next));
            }
        }
        return List.copyOf(applied);
    }

    synchronized List<WiredOpacityState> applyUser(
            int userId, Collection<HabboItem> items, int opacity, boolean clickThrough) {
        if (userId <= 0 || this.disposed) {
            return List.of();
        }

        VisualState next = new VisualState(opacity, clickThrough);
        Map<Integer, VisualState> states = this.userStates.computeIfAbsent(userId, ignored -> new HashMap<>());
        ArrayList<WiredOpacityState> applied = new ArrayList<>();
        for (HabboItem item : validItems(items)) {
            if (states.containsKey(item.getId()) || reserveState(item.getId())) {
                states.put(item.getId(), next);
                applied.add(state(item, next));
            }
        }
        if (states.isEmpty()) {
            this.userStates.remove(userId);
        }
        return List.copyOf(applied);
    }

    synchronized List<WiredOpacityState> effective(int userId, Collection<Integer> itemIds) {
        if (this.disposed || itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }

        Map<Integer, VisualState> personal = this.userStates.get(userId);
        ArrayList<WiredOpacityState> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer itemId : itemIds) {
            if (itemId == null || !seen.add(itemId)) {
                continue;
            }
            HabboItem item = this.room.getHabboItem(itemId);
            if (!isCurrentRoomItem(item)) {
                continue;
            }
            VisualState state = personal == null ? null : personal.get(itemId);
            if (state == null) {
                state = this.globalStates.getOrDefault(itemId, VisualState.DEFAULT);
            }
            result.add(state(item, state));
        }
        return List.copyOf(result);
    }

    synchronized List<WiredOpacityState> snapshot(int userId) {
        if (this.disposed) {
            return List.of();
        }

        Set<Integer> itemIds = new HashSet<>(this.globalStates.keySet());
        Map<Integer, VisualState> personal = this.userStates.get(userId);
        if (personal != null) {
            itemIds.addAll(personal.keySet());
        }
        return effective(userId, itemIds.stream().sorted().toList());
    }

    synchronized void forgetItem(HabboItem item) {
        if (item == null) {
            return;
        }
        int itemId = item.getId();
        if (this.globalStates.remove(itemId) != null) {
            this.stateCount--;
        }
        for (Map<Integer, VisualState> states : this.userStates.values()) {
            if (states.remove(itemId) != null) {
                this.stateCount--;
            }
        }
        this.userStates.values().removeIf(Map::isEmpty);
    }

    synchronized void forgetUser(int userId) {
        Map<Integer, VisualState> removed = this.userStates.remove(userId);
        if (removed != null) {
            this.stateCount -= removed.size();
        }
    }

    synchronized void dispose() {
        this.disposed = true;
        this.globalStates.clear();
        this.userStates.clear();
        this.stateCount = 0;
    }

    synchronized int stateCount() {
        return this.stateCount;
    }

    private List<HabboItem> validItems(Collection<HabboItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(this::isCurrentRoomItem)
                .distinct()
                .sorted(java.util.Comparator.comparingInt(HabboItem::getId))
                .toList();
    }

    private boolean isCurrentRoomItem(HabboItem item) {
        return item != null
                && item.getId() > 0
                && item.getRoomId() == this.room.getId()
                && item.getBaseItem() != null
                && this.room.getHabboItem(item.getId()) == item;
    }

    private boolean reserveState(int itemId) {
        if (this.stateCount >= this.maximumStates) {
            LOGGER.warn(
                    "Opacity state limit reached: room={}, configuredLimit={}, rejectedItem={}",
                    this.room.getId(),
                    this.maximumStates,
                    itemId);
            return false;
        }
        this.stateCount++;
        return true;
    }

    private static WiredOpacityState state(HabboItem item, VisualState state) {
        return new WiredOpacityState(
                item.getId(),
                item.getBaseItem().getType() == FurnitureType.WALL,
                state.opacity(),
                state.clickThrough());
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
                        configuration.getInt("wired.opacity.max_states_per_room", DEFAULT_MAXIMUM_STATES)));
    }

    private record VisualState(int opacity, boolean clickThrough) {
        private static final VisualState DEFAULT = new VisualState(100, false);

        private VisualState {
            opacity = Math.max(0, Math.min(100, opacity));
        }

        private boolean isDefault() {
            return this.opacity == 100 && !this.clickThrough;
        }
    }
}
