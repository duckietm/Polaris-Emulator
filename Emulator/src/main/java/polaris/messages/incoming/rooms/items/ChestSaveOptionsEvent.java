package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestStorage;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * Lock, auto-lock and capacity from the chest window (AIR {@code WiredChestWrapperView}):
 * {@code int chestId, bool locked, bool autoLock, int capacity}.
 *
 * <p>The owner sets all three. Anyone else may only lock: the room owner, or someone who may edit
 * the room's wired when the chest answers wired. Nobody but the owner ever unlocks. There is no
 * reply; the new state reaches the room with the furni.
 */
public class ChestSaveOptionsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        boolean locked = this.packet.readBoolean();
        boolean autoLock = this.packet.readBoolean();
        int capacity = this.packet.readInt();

        UserItem item = room.getUserItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        ChestStorage c = chest.getContents();
        boolean isOwner = user.getUserInfo().getId() == chest.getUserId();

        if (isOwner) {
            if (capacity < 0 || capacity > c.getCapacityMax()) return;
            synchronized (c) {
                c.setLocked(locked);
                c.setAutoLock(autoLock);
                c.setCapacity(capacity == 0 ? c.getCapacityMax() : capacity);
            }
        } else {
            boolean mayLock = room.isOwner(user) || (c.isWiredEnabled() && room.canModifyWired(user));
            if (!mayLock || !locked || c.isLocked()) return;
            c.setLocked(true);
        }

        chest.persistContents();
        chest.publishSettings(room);
    }
}
