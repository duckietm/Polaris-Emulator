package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredTriggerBotReachedFurni extends InteractionWiredTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredTriggerBotReachedFurni.class);
    private static final int BOT_SOURCE_NAME = 100;
    private static final int BOT_SOURCE_SELECTOR = 200;

    public final static WiredTriggerType type = WiredTriggerType.BOT_REACHED_STF;

    private final Set<HabboItem> items;
    private String botName = "";
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = BOT_SOURCE_NAME;

    public WiredTriggerBotReachedFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredTriggerBotReachedFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
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
        message.appendString(this.botName);
        message.appendInt(2);
        message.appendInt(this.furniSource);
        message.appendInt(this.botSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects(this.getX(), this.getY())) {
                if (effect.requiresTriggeringUser()) {
                    invalidTriggers.add(effect.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.botName = settings.getStringParam();
        int[] params = settings.getIntParams();
        this.furniSource = (params.length > 0)
                ? this.normalizeFurniSource(params[0])
                : ((settings.getFurniIds().length > 0) ? WiredSourceUtil.SOURCE_SELECTED : WiredSourceUtil.SOURCE_TRIGGER);
        this.botSource = (params.length > 1) ? this.normalizeBotSource(params[1]) : BOT_SOURCE_NAME;

        this.items.clear();
        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        int count = settings.getFurniIds().length;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();

        // Get the furniture item the bot walked onto
        HabboItem sourceItem = event.getSourceItem().orElse(null);
        if (sourceItem == null || roomUnit == null || room == null) {
            return false;
        }

        if (!this.matchesBotSource(event, roomUnit, room) || !isCorrectBotForName(roomUnit, room)) {
            return false;
        }

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
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.botName,
            this.furniSource,
            this.botSource,
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.botName = data.botName;
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            this.botSource = this.normalizeBotSource(data.botSource);
            for (Integer id: data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }

            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length == 1) {
                this.botName = data[0];
            } else if (data.length == 2) {
                this.botName = data[0];

                String[] items = data[1].split(";");

                for (String id : items) {
                    try {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item != null)
                            this.items.add(item);
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }
            }

            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.botSource = BOT_SOURCE_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.botName = "";
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = BOT_SOURCE_NAME;
    }

    static class JsonData {
        String botName;
        int furniSource;
        int botSource;
        List<Integer> itemIds;

        public JsonData(String botName, int furniSource, int botSource, List<Integer> itemIds) {
            this.botName = botName;
            this.furniSource = furniSource;
            this.botSource = botSource;
            this.itemIds = itemIds;
        }
    }

    private boolean matchesBotSource(WiredEvent event, RoomUnit roomUnit, Room room) {
        if (this.botSource == BOT_SOURCE_SELECTOR) {
            return WiredTriggerSourceUtil.containsUser(
                    WiredTriggerSourceUtil.resolveUsers(this, event, WiredSourceUtil.SOURCE_SELECTOR, null),
                    roomUnit);
        }

        return true;
    }

    private boolean isCorrectBotForName(RoomUnit roomUnit, Room room) {
        if (this.botSource != BOT_SOURCE_NAME) {
            return true;
        }

        return room.getBots(this.botName).stream().anyMatch(bot -> bot.getRoomUnit() == roomUnit);
    }

    private int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTED || value == WiredSourceUtil.SOURCE_SELECTOR) {
            return value;
        }

        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeBotSource(int value) {
        return (value == BOT_SOURCE_SELECTOR) ? BOT_SOURCE_SELECTOR : BOT_SOURCE_NAME;
    }
}
