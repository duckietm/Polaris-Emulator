package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        boolean locked = this.packet.readBoolean();
        boolean autoLock = this.packet.readBoolean();
        int capacity = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        ChestStorage c = chest.getContents();
        boolean isOwner = habbo.getHabboInfo().getId() == chest.getUserId();

        if (isOwner) {
            if (capacity < 0 || capacity > c.getCapacityMax()) return;
            synchronized (c) {
                c.setLocked(locked);
                c.setAutoLock(autoLock);
                c.setCapacity(capacity == 0 ? c.getCapacityMax() : capacity);
            }
        } else {
            boolean mayLock = room.isOwner(habbo) || (c.isWiredEnabled() && room.canModifyWired(habbo));
            if (!mayLock || !locked || c.isLocked()) return;
            c.setLocked(true);
        }

        chest.persistContents();
        chest.publishSettings(room);
    }
}
