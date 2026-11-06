package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredTriggerType;
import polaris.hotel.wired.core.WiredEvent;
import polaris.messages.ServerMessage;

/**
 * Official room initialized (28): fires once when the room loads (someone enters after it was unloaded,
 * or a :reload), before any other trigger. No settings.
 */
public class WiredTriggerRoomInitialized extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.ROOM_INITIALIZED;

    public WiredTriggerRoomInitialized(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerRoomInitialized(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return "";
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {}

    @Override
    public void onPickUp() {}

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
        message.appendString("");
        message.appendInt(0);
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
        return true;
    }
}
