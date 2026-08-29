package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.WiredChestLockStateComposer;

/**
 * Locks or unlocks the room's wired chests from the chests tab. Reads {@code bool lock, bool all}.
 *
 * <p>{@code all = false} touches only the chests this player owns, which needs room rights.
 * {@code all = true} touches every chest in the room and is reserved for the room owner (or staff
 * holding {@link Permission#ACC_ANYROOMOWNER}) — it can freeze furni belonging to other people.
 *
 * <p>A locked chest keeps answering wired effects but refuses everything a player does by hand,
 * deposits included. That is the point of the switch: freeze a chest without breaking the room that
 * runs on it.
 */
public class WiredChestLockEvent extends MessageHandler {
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

        boolean lock = this.packet.readBoolean();
        boolean all = this.packet.readBoolean();

        if (all) {
            if (!room.isOwner(habbo) && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)) return;
        } else if (!room.hasRights(habbo)) {
            return;
        }

        int ownerId = habbo.getHabboInfo().getId();
        int affected = 0;

        // Enumerated over the floor items rather than RoomSpecialTypes: that index matches on the
        // exact class, and every chest here is one of the concrete subclasses.
        for (HabboItem item : room.getFloorItems()) {
            if (!(item instanceof InteractionWiredChest chest)) continue;
            if (!all && chest.getUserId() != ownerId) continue;
            if (chest.getContents().isLocked() == lock) continue;

            chest.getContents().setLocked(lock);
            chest.persistContents();
            affected++;
        }

        this.client.sendResponse(new WiredChestLockStateComposer(lock, all, affected));
    }
}
