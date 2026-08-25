package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredTriggerFurniStateToggled extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.STATE_CHANGED;
    private static final int MODE_ALL_STATES = 0;
    private static final int MODE_SAVED_STATE = 1;

    private Set<StateSnapshot> snapshots;
    private int triggerMode = MODE_ALL_STATES;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredTriggerFurniStateToggled(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.snapshots = new LinkedHashSet<>();
    }

    public WiredTriggerFurniStateToggled(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.snapshots = new LinkedHashSet<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event.isTriggeredByEffect()) {
            return false;
        }

        HabboItem sourceItem = event.getSourceItem().orElse(null);
        if (sourceItem == null) {
            return false;
        }

        StateSnapshot snapshot = this.getSnapshot(sourceItem.getId());
        if (!this.matchesSourceItem(event, sourceItem)) {
            return false;
        }

        if (this.triggerMode == MODE_SAVED_STATE) {
            if (snapshot == null) {
                return false;
            }
            return snapshot.state.equals(this.normalizeState(sourceItem.getExtradata()));
        }

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.triggerMode,
            this.furniSource,
            new ArrayList<>(this.snapshots)
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.snapshots = new LinkedHashSet<>();
        this.triggerMode = MODE_ALL_STATES;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.triggerMode = (data != null) ? data.triggerMode : MODE_ALL_STATES;
            this.furniSource = (data != null) ? this.normalizeFurniSource(data.furniSource) : WiredSourceUtil.SOURCE_TRIGGER;

            if (data != null && data.snapshots != null && !data.snapshots.isEmpty()) {
                for (StateSnapshot snapshot : data.snapshots) {
                    if (snapshot == null) continue;

                    HabboItem item = room.getHabboItem(snapshot.itemId);
                    if (item != null) {
                        this.snapshots.add(new StateSnapshot(item.getId(), snapshot.state));
                    }
                }
            } else if (data != null && data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) {
                        this.snapshots.add(this.captureSnapshot(item));
                    }
                }
            }

            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.snapshots.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            if (wiredData.split(":").length >= 3) {
                super.setDelay(Integer.parseInt(wiredData.split(":")[0]));

                if (!wiredData.split(":")[2].equals("\t")) {
                    for (String s : wiredData.split(":")[2].split(";")) {
                        if (s.isEmpty()) {
                            continue;
                        }

                        HabboItem item = room.getHabboItem(Integer.parseInt(s));

                        if (item != null) {
                            this.snapshots.add(this.captureSnapshot(item));
                        }
                    }
                }
            }

            this.furniSource = this.snapshots.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.snapshots.clear();
        this.triggerMode = MODE_ALL_STATES;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        Set<StateSnapshot> snapshotsToRemove = new HashSet<>();

        for (StateSnapshot snapshot : this.snapshots) {
            HabboItem item = room.getHabboItem(snapshot.itemId);
            if (item == null || item.getRoomId() != this.getRoomId()) {
                snapshotsToRemove.add(snapshot);
                continue;
            }
        }

        for (StateSnapshot snapshot : snapshotsToRemove) {
            this.snapshots.remove(snapshot);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.snapshots.size());
        for (StateSnapshot snapshot : this.snapshots) {
            message.appendInt(snapshot.itemId);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.triggerMode);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.snapshots.clear();
        this.triggerMode = (settings.getIntParams().length > 0 && settings.getIntParams()[0] == MODE_SAVED_STATE)
                ? MODE_SAVED_STATE
                : MODE_ALL_STATES;
        this.furniSource = (settings.getIntParams().length > 1)
                ? this.normalizeFurniSource(settings.getIntParams()[1])
                : ((settings.getFurniIds().length > 0) ? WiredSourceUtil.SOURCE_SELECTED : WiredSourceUtil.SOURCE_TRIGGER);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return true;
        }

        int count = settings.getFurniIds().length;

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.snapshots.add(this.captureSnapshot(item));
            }
        }

        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    private StateSnapshot captureSnapshot(HabboItem item) {
        return new StateSnapshot(item.getId(), this.normalizeState(item.getExtradata()));
    }

    private StateSnapshot getSnapshot(int itemId) {
        for (StateSnapshot snapshot : this.snapshots) {
            if (snapshot.itemId == itemId) {
                return snapshot;
            }
        }

        return null;
    }

    private String normalizeState(String state) {
        return (state == null) ? "" : state;
    }

    private boolean matchesSourceItem(WiredEvent event, HabboItem sourceItem) {
        List<HabboItem> selectedItems = new ArrayList<>();

        if (event.getRoom() != null) {
            for (StateSnapshot snapshot : this.snapshots) {
                HabboItem item = event.getRoom().getHabboItem(snapshot.itemId);
                if (item != null) {
                    selectedItems.add(item);
                }
            }
        }

        for (HabboItem item : WiredTriggerSourceUtil.resolveItems(this, event, this.furniSource, selectedItems)) {
            if (item != null && item.getId() == sourceItem.getId()) {
                return true;
            }
        }

        return false;
    }

    private int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTED || value == WiredSourceUtil.SOURCE_SELECTOR) {
            return value;
        }

        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int triggerMode;
        int furniSource;
        List<StateSnapshot> snapshots;
        List<Integer> itemIds;

        public JsonData() {
        }

        public JsonData(List<Integer> itemIds) {
            this.itemIds = itemIds;
        }

        public JsonData(int triggerMode, int furniSource, List<StateSnapshot> snapshots) {
            this.triggerMode = triggerMode;
            this.furniSource = furniSource;
            this.snapshots = snapshots;
        }
    }

    static class StateSnapshot {
        int itemId;
        String state;

        public StateSnapshot() {
        }

        public StateSnapshot(int itemId, String state) {
            this.itemId = itemId;
            this.state = (state == null) ? "" : state;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(this.itemId);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof StateSnapshot)) {
                return false;
            }

            StateSnapshot that = (StateSnapshot) object;
            return this.itemId == that.itemId;
        }
    }
}
