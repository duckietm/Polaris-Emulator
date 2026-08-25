package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerHabboSaysKeyword extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.SAY_SOMETHING;
    private static final int MATCH_CONTAINS = 0;
    private static final int MATCH_EXACT = 1;
    private static final int MATCH_ALL_WORDS = 2;

    private boolean hideMessage = false;
    private boolean ownerOnly = false;
    private String key = "";
    private int matchMode = MATCH_CONTAINS;

    public WiredTriggerHabboSaysKeyword(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerHabboSaysKeyword(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if ((this.matchMode != MATCH_ALL_WORDS) && this.key.length() <= 0) {
            return false;
        }

        String text = event.getText().orElse(null);
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();

        if (text == null || roomUnit == null || room == null || !this.matchesText(text)) {
            return false;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        return !this.ownerOnly || (habbo != null && room.getOwnerId() == habbo.getHabboInfo().getId());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.hideMessage,
            this.ownerOnly,
            this.key,
            this.matchMode
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.ownerOnly = data.ownerOnly;
            this.hideMessage = data.hideMessage;
            this.key = data.key;
            this.matchMode = this.normalizeMatchMode(data.matchMode);
        } else {
            String[] data = wiredData.split("\t");

            if (data.length == 2) {
                this.ownerOnly = data[0].equalsIgnoreCase("1");
                this.key = data[1];
                this.hideMessage = false;
                this.matchMode = MATCH_CONTAINS;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.hideMessage = false;
        this.ownerOnly = false;
        this.key = "";
        this.matchMode = MATCH_CONTAINS;
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
        message.appendString(this.key);
        message.appendInt(3);
        message.appendInt(this.matchMode);
        message.appendInt(this.hideMessage ? 1 : 0);
        message.appendInt(this.ownerOnly ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.matchMode = (params.length > 0) ? this.normalizeMatchMode(params[0]) : MATCH_CONTAINS;
        this.hideMessage = (params.length > 1) && (params[1] == 1);
        this.ownerOnly = (params.length > 2) && (params[2] == 1);
        this.key = (this.matchMode == MATCH_ALL_WORDS) ? "" : settings.getStringParam();

        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    public boolean isHideMessage() {
        return this.hideMessage;
    }

    public boolean isOwnerOnly() {
        return this.ownerOnly;
    }

    public String getKey() {
        return this.key;
    }

    public int getMatchMode() {
        return this.matchMode;
    }

    private boolean matchesText(String text) {
        String normalizedText = text.toLowerCase().trim();
        String normalizedKey = this.key.toLowerCase().trim();

        switch (this.matchMode) {
            case MATCH_EXACT:
                return normalizedText.equals(normalizedKey);
            case MATCH_ALL_WORDS:
                return !normalizedText.isEmpty();
            case MATCH_CONTAINS:
            default:
                return normalizedText.contains(normalizedKey);
        }
    }

    private int normalizeMatchMode(int value) {
        if (value < MATCH_CONTAINS || value > MATCH_ALL_WORDS) {
            return MATCH_CONTAINS;
        }

        return value;
    }

    static class JsonData {
        boolean hideMessage;
        boolean ownerOnly;
        String key;
        int matchMode;

        public JsonData(boolean hideMessage, boolean ownerOnly, String key, int matchMode) {
            this.hideMessage = hideMessage;
            this.ownerOnly = ownerOnly;
            this.key = key;
            this.matchMode = matchMode;
        }
    }
}
