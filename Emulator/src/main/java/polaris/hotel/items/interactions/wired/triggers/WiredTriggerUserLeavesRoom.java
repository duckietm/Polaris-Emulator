package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredTriggerType;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;

public class WiredTriggerUserLeavesRoom extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.LEAVE_ROOM;

    private String username = "";

    public WiredTriggerUserLeavesRoom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUserLeavesRoom(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();
        User user = room.getUser(roomUnit);

        if (user != null) {
            if (this.username.length() > 0) {
                return user.getUserInfo().getUsername().equalsIgnoreCase(this.username);
            }

            return true;
        }
        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.username));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.username = data.username;
        } else {
            this.username = wiredData;
        }
    }

    @Override
    public void onPickUp() {
        this.username = "";
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
        message.appendString(this.username);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.username = settings.getStringParam();

        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        String username;

        public JsonData(String username) {
            this.username = username;
        }
    }
}
