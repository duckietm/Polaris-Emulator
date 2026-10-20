package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredWebApiOwnership;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomPlayTestSupport;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectRemoveConfirmComposer;

public class RoomPickupItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // 10 = floorItem and 20 = wallItem
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        // Official: the pick-up sent again after the "delete this variable" confirmation carries true.
        boolean confirmed = this.packet.bytesAvailable() > 0 && this.packet.readBoolean();

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        // AIR playtest mode: nothing is picked up or ejected, the user's own furni included.
        if (RoomPlayTestSupport.isRestricted(this.client.getHabbo())) return;

        HabboItem item = room.getHabboItem(itemId);

        if (item == null) return;

        if (item instanceof InteractionPostIt) return;

        // A Place Furni copy: only the room owner and the owner of the box that placed it may pick it
        // up, which removes it (there is nothing to give back). It never takes a new owner.
        if (item.isTemporary()) {
            int habboId = this.client.getHabbo().getHabboInfo().getId();

            if (habboId == room.getOwnerId()
                    || (item.getTemporaryOwnerId() > 0 && habboId == item.getTemporaryOwnerId())) {
                room.ejectUserItem(item);
            }
            return;
        }

        if (item.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
            if (this.askToConfirm(item, confirmed)) return;
            room.pickUpItem(item, this.client.getHabbo());
        } else {
            if (room.hasRights(this.client.getHabbo())) {
                if (this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)
                        && !WiredWebApiOwnership.isWebApiItem(item)) {
                    item.setUserId(this.client.getHabbo().getHabboInfo().getId());
                } else if (this.client.getHabbo().getHabboInfo().getId() != room.getOwnerId()
                        && item.getUserId() == room.getOwnerId()) {
                    return;
                }

                if (this.askToConfirm(item, confirmed)) return;
                room.ejectUserItem(item);
            }
        }
    }

    /**
     * Official: picking up a permanent or shared variable deletes the values stored in it, so the client asks first.
     * The first pick-up answers with the confirmation; the confirmed one goes through.
     */
    private boolean askToConfirm(HabboItem item, boolean confirmed) {
        if (confirmed || !storesVariableData(item)) return false;

        this.client.sendResponse(new ObjectRemoveConfirmComposer(
                item.getBaseItem().getType() == FurnitureType.WALL,
                item.getId(),
                "wiredfurni.variables.pickup.title",
                "wiredfurni.variables.pickup.body"));
        return true;
    }

    static boolean storesVariableData(HabboItem item) {
        return (item instanceof WiredExtraUserVariable user && user.isPermanentAvailability())
                || (item instanceof WiredExtraFurniVariable furni && furni.isPermanentAvailability())
                || (item instanceof WiredExtraRoomVariable room && room.isPermanentAvailability());
    }
}
