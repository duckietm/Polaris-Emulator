package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveException;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class WiredTriggerReceiveSignal extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.RECEIVE_SIGNAL;

    private static final long ACTIVATION_PULSE_MS = 300L;
    private static final String ANTENNA_INTERACTION = "antenna";
    private static final String REQUIRE_ANTENNA_ERROR = "You can only select antenna furni.";

    private int channel = 0; // signal channel (0-based)
    private Set<HabboItem> items;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private final AtomicLong activationToken = new AtomicLong();

    public WiredTriggerReceiveSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredTriggerReceiveSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event.getType() != WiredEvent.Type.SIGNAL_RECEIVED) return false;

        List<HabboItem> resolvedAntennas = WiredTriggerSourceUtil.resolveItems(this, event, this.furniSource, this.items).stream()
                .filter(this::isAntennaItem)
                .collect(Collectors.toList());

        if (!resolvedAntennas.isEmpty()) {
            return resolvedAntennas.stream()
                    .anyMatch(item -> item != null && item.getId() == event.getSignalChannel());
        }

        return this.channel > 0 && event.getSignalChannel() == this.channel;
    }

    public int getChannel() {
        return channel;
    }

    public boolean unlinkAntenna(int antennaItemId) {
        if (antennaItemId <= 0) {
            return false;
        }

        boolean changed = false;

        if (!this.items.isEmpty()) {
            Set<HabboItem> itemsToRemove = new HashSet<>();

            for (HabboItem item : this.items) {
                if (item == null || item.getId() == antennaItemId) {
                    itemsToRemove.add(item);
                }
            }

            if (!itemsToRemove.isEmpty()) {
                this.items.removeAll(itemsToRemove);
                changed = true;
            }
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            int nextChannel = 0;

            if (!this.items.isEmpty()) {
                HabboItem firstItem = this.items.iterator().next();
                nextChannel = (firstItem != null) ? firstItem.getId() : 0;
            }

            if (this.channel != nextChannel) {
                this.channel = nextChannel;
                changed = true;
            }
        } else if (this.channel == antennaItemId) {
            this.channel = 0;
            changed = true;
        }

        if (changed) {
            this.needsUpdate(true);
        }

        return changed;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return false;
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
        int senderCount = 0;
        try {
            if (room != null && room.getRoomSpecialTypes() != null) {
                senderCount = this.getSenderCount(room);
            }
        } catch (Exception e) {
        }

        Set<HabboItem> itemsToRemove = new HashSet<>();
        for (HabboItem item : this.items) {
            if (item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null) {
                itemsToRemove.add(item);
            }
        }
        for (HabboItem item : itemsToRemove) {
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
        message.appendInt(4);
        message.appendInt(channel);
        message.appendInt(senderCount);
        message.appendInt(RoomSpecialTypes.MAX_SENDERS_PER_RECEIVER);
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

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        int count = settings.getFurniIds().length;

        int[] params = settings.getIntParams();
        this.channel = params.length > 0 ? params[0] : 0;
        this.furniSource = (params.length > 1)
                ? this.normalizeFurniSource(params[params.length - 1])
                : WiredSourceUtil.SOURCE_SELECTED;

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED && room != null) {
            for (int i = 0; i < count; i++) {
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
                if (item == null) continue;
                if (!this.isAntennaItem(item)) throw new WiredTriggerSaveException(REQUIRE_ANTENNA_ERROR);

                this.items.add(item);
            }
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED && !this.items.isEmpty()) {
            HabboItem firstItem = this.items.iterator().next();
            this.channel = (firstItem != null) ? firstItem.getId() : this.channel;
        }

        return true;
    }

    @Override
    public void activateBox(Room room, RoomUnit roomUnit, long millis) {
        if (roomUnit != null) {
            this.addUserExecutionCache(roomUnit.getId(), millis);
        }

        if (room == null || room.isHideWired() || this.getBaseItem().getStateCount() <= 1) {
            return;
        }

        final long token = this.activationToken.incrementAndGet();

        if ("1".equals(this.getExtradata())) {
            this.setExtradata("0");
            room.sendComposer(new ItemStateComposer(this).compose());
        }

        this.setExtradata("1");
        room.sendComposer(new ItemStateComposer(this).compose());

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded()) return;
            if (this.activationToken.get() != token) return;

            this.setExtradata("0");
            room.sendComposer(new ItemStateComposer(this).compose());
        }, ACTIVATION_PULSE_MS);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                channel,
                furniSource,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new LinkedHashSet<>();
        String wiredData = set.getString("wired_data");
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.channel = data.channel;
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) this.items.add(item);
                }
            }

            if (this.furniSource != WiredSourceUtil.SOURCE_SELECTOR) this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED && !this.items.isEmpty() && this.channel <= 0) {
                HabboItem firstItem = this.items.iterator().next();
                if (firstItem != null) this.channel = firstItem.getId();
            }
        }
    }

    @Override
    public void onPickUp() {
        this.channel = 0;
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
    }

    static class JsonData {
        int channel;
        int furniSource;
        List<Integer> itemIds;

        public JsonData() {}

        public JsonData(int channel, int furniSource, List<Integer> itemIds) {
            this.channel = channel;
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }

    private int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTED || value == WiredSourceUtil.SOURCE_SELECTOR) {
            return value;
        }

        return WiredSourceUtil.SOURCE_SELECTED;
    }

    private boolean isAntennaItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interaction = item.getBaseItem().getInteractionType().getName();
        return interaction != null && ANTENNA_INTERACTION.equalsIgnoreCase(interaction);
    }

    private int getSenderCount(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return 0;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED && !this.items.isEmpty()) {
            List<Integer> antennaIds = new ArrayList<>();

            for (HabboItem item : this.items) {
                if (this.isAntennaItem(item)) {
                    antennaIds.add(item.getId());
                }
            }

            if (!antennaIds.isEmpty()) {
                return room.getRoomSpecialTypes().countSendersTargetingAnyReceiver(antennaIds);
            }
        }

        if (this.channel > 0) {
            return room.getRoomSpecialTypes().countSendersTargetingReceiver(this.channel);
        }

        return 0;
    }
}
