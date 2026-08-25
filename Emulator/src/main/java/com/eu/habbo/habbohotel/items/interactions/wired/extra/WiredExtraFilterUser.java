package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraFilterUser extends InteractionWiredExtra {
    public static final int CODE = 57;
    private static final int MAX_FILTER_AMOUNT = 10000;

    private int amount = 0;

    public WiredExtraFilterUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraFilterUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int value = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : 0;

        if (value == 0 && settings.getStringParam() != null && !settings.getStringParam().isEmpty()) {
            try {
                value = Integer.parseInt(settings.getStringParam());
            } catch (NumberFormatException ignored) {
                value = 0;
            }
        }

        this.amount = normalizeAmount(value);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.amount));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.amount);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
            this.amount = normalizeAmount((data != null) ? data.amount : 0);
            return;
        }

        try {
            this.amount = normalizeAmount(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.amount = 0;
        }
    }

    @Override
    public void onPickUp() {
        this.amount = 0;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getAmount() {
        return this.amount;
    }

    private static int normalizeAmount(int value) {
        return Math.max(0, Math.min(MAX_FILTER_AMOUNT, value));
    }

    static class JsonData {
        int amount;

        JsonData(int amount) {
            this.amount = amount;
        }
    }
}
