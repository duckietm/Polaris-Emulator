package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

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

public class WiredTriggerBotReachedHabbo extends InteractionWiredTrigger {
    public final static WiredTriggerType type = WiredTriggerType.BOT_REACHED_AVTR;
    private static final int BOT_SOURCE_NAME = 100;
    private static final int BOT_SOURCE_SELECTOR = 200;

    private String botName = "";
    private int botSource = BOT_SOURCE_NAME;

    public WiredTriggerBotReachedHabbo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerBotReachedHabbo(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(1);
        message.appendInt(this.botSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.botName = settings.getStringParam();
        this.botSource = (settings.getIntParams().length > 0) ? this.normalizeBotSource(settings.getIntParams()[0]) : BOT_SOURCE_NAME;

        return true;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();

        if (roomUnit == null || room == null) {
            return false;
        }

        if (this.botSource == BOT_SOURCE_SELECTOR) {
            return WiredTriggerSourceUtil.containsUser(
                    WiredTriggerSourceUtil.resolveUsers(this, event, WiredSourceUtil.SOURCE_SELECTOR, null),
                    roomUnit);
        }

        return room.getBots(this.botName).stream().anyMatch(bot -> bot.getRoomUnit() == roomUnit);
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
            this.botSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.botName = data.botName;
            this.botSource = this.normalizeBotSource(data.botSource);
        } else {
            this.botName = wiredData;
            this.botSource = BOT_SOURCE_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.botSource = BOT_SOURCE_NAME;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        String botName;
        int botSource;

        public JsonData(String botName, int botSource) {
            this.botName = botName;
            this.botSource = botSource;
        }
    }

    private int normalizeBotSource(int value) {
        return (value == BOT_SOURCE_SELECTOR) ? BOT_SOURCE_SELECTOR : BOT_SOURCE_NAME;
    }
}
