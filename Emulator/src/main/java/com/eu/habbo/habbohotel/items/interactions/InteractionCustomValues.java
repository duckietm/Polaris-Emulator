package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public abstract class InteractionCustomValues extends HabboItem {
    public final Map<String, String> values = new HashMap<>();

    public InteractionCustomValues(ResultSet set, Item baseItem, Map<String, String> defaultValues) throws SQLException {
        super(set, baseItem);

        this.values.putAll(defaultValues);

        for (String s : set.getString("extra_data").split(";")) {
            String[] data = s.split("=");

            if (data.length == 2) {
                this.values.put(data[0], data[1]);
            }
        }
    }

    public InteractionCustomValues(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells, Map<String, String> defaultValues) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.values.putAll(defaultValues);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void run() {
        this.setExtradata(this.toExtraData());

        super.run();
    }

    public String toExtraData() {
        StringBuilder data = new StringBuilder();
        synchronized (this.values) {
            for (Map.Entry<String, String> set : this.values.entrySet()) {
                data.append(set.getKey()).append("=").append(set.getValue()).append(";");
            }
        }

        return data.toString();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(1 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(this.values.size());
        for (Map.Entry<String, String> set : this.values.entrySet()) {
            serverMessage.appendString(set.getKey());
            serverMessage.appendString(set.getValue());
        }

        super.serializeExtradata(serverMessage);
    }

    public void onCustomValuesSaved(Room room, GameClient client, Map<String, String> oldValues) {

    }
}
