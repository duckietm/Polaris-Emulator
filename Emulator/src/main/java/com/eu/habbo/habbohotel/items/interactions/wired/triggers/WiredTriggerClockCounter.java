package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredTriggerClockCounter extends InteractionWiredTrigger {
    private static final int MAX_MINUTES = 99;
    private static final int MAX_HALF_SECOND_STEPS = 119;

    public static final WiredTriggerType type = WiredTriggerType.CLOCK_COUNTER;

    private final Set<HabboItem> items;
    private int minutes = 0;
    private int halfSecondSteps = 0;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredTriggerClockCounter(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredTriggerClockCounter(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        HabboItem sourceItem = event.getSourceItem().orElse(null);

        if (!(sourceItem instanceof InteractionGameUpCounter)) {
            return false;
        }

        if (((InteractionGameUpCounter) sourceItem).getCurrentTimeInMs() != this.getTargetTimeInMs()) {
            return false;
        }

        return WiredTriggerSourceUtil.resolveItems(this, event, this.furniSource, this.items).stream()
                .anyMatch(item -> item != null && item.getId() == sourceItem.getId());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.minutes);
        message.appendInt(this.halfSecondSteps);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.minutes = (params.length > 0) ? this.normalizeMinutes(params[0]) : 0;
        this.halfSecondSteps = (params.length > 1) ? this.normalizeHalfSecondSteps(params[1]) : 0;
        this.furniSource = (params.length > 2)
                ? this.normalizeFurniSource(params[2])
                : ((settings.getFurniIds().length > 0) ? WiredSourceUtil.SOURCE_SELECTED : WiredSourceUtil.SOURCE_TRIGGER);

        this.items.clear();

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);

            if (!(item instanceof InteractionGameUpCounter)) {
                throw new WiredTriggerSaveException("wiredfurni.error.require_counter_furni");
            }

            this.items.add(item);
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.minutes,
                this.halfSecondSteps,
                this.furniSource,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.minutes = 0;
        this.halfSecondSteps = 0;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (!wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.minutes = this.normalizeMinutes(data.minutes);
        this.halfSecondSteps = this.normalizeHalfSecondSteps(data.halfSecondSteps);
        this.furniSource = this.normalizeFurniSource(data.furniSource);

        if (data.itemIds == null) {
            return;
        }

        for (Integer id : data.itemIds) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionGameUpCounter) {
                this.items.add(item);
            }
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.minutes = 0;
        this.halfSecondSteps = 0;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    private void refresh(Room room) {
        Set<HabboItem> remove = new HashSet<>();

        for (HabboItem item : this.items) {
            HabboItem roomItem = room.getHabboItem(item.getId());
            if (!(roomItem instanceof InteractionGameUpCounter)) {
                remove.add(item);
            }
        }

        for (HabboItem item : remove) {
            this.items.remove(item);
        }
    }

    private int getTargetTimeInMs() {
        return (this.minutes * 60_000) + (this.halfSecondSteps * 500);
    }

    private int normalizeMinutes(int value) {
        return Math.max(0, Math.min(MAX_MINUTES, value));
    }

    private int normalizeHalfSecondSteps(int value) {
        return Math.max(0, Math.min(MAX_HALF_SECOND_STEPS, value));
    }

    private int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTED || value == WiredSourceUtil.SOURCE_SELECTOR) {
            return value;
        }

        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int minutes;
        int halfSecondSteps;
        int furniSource;
        List<Integer> itemIds;

        public JsonData(int minutes, int halfSecondSteps, int furniSource, List<Integer> itemIds) {
            this.minutes = minutes;
            this.halfSecondSteps = halfSecondSteps;
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }
}
