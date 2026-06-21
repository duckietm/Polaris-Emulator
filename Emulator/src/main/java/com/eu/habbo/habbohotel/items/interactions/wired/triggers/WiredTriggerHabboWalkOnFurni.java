package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredTriggerHabboWalkOnFurni extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.WALKS_ON_FURNI;

    private final Set<HabboItem> items;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredTriggerHabboWalkOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredTriggerHabboWalkOnFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        HabboItem sourceItem = event.getSourceItem().orElse(null);
        Room room = event.getRoom();
        return WiredTriggerSourceUtil.containsItemOrTile(
                room,
                WiredTriggerSourceUtil.resolveItems(this, event, this.furniSource, this.items),
                sourceItem);
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
        Set<HabboItem> items = new HashSet<>();

        if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()) == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                    items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
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
        this.items.clear();
        this.furniSource = (settings.getIntParams().length > 0)
                ? this.normalizeFurniSource(settings.getIntParams()[0])
                : ((settings.getFurniIds().length > 0) ? WiredSourceUtil.SOURCE_SELECTED : WiredSourceUtil.SOURCE_TRIGGER);

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        int count = settings.getFurniIds().length;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.furniSource,
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            for (Integer id: data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            if (wiredData.split(":").length >= 3) {
                super.setDelay(Integer.parseInt(wiredData.split(":")[0]));

                if (!wiredData.split(":")[2].equals("\t")) {
                    for (String s : wiredData.split(":")[2].split(";")) {
                        if (s.isEmpty())
                            continue;

                        try {
                            HabboItem item = room.getHabboItem(Integer.parseInt(s));

                            if (item != null)
                                this.items.add(item);
                        } catch (Exception e) {
                        }
                    }
                }
            }

            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        int furniSource;
        List<Integer> itemIds;

        public JsonData(int furniSource, List<Integer> itemIds) {
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }

    private int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTED || value == WiredSourceUtil.SOURCE_SELECTOR) {
            return value;
        }

        return WiredSourceUtil.SOURCE_TRIGGER;
    }
}
