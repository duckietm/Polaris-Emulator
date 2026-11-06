package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.interactions.InteractionCrackable;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.ObjectRemoveConfirmComposer;

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

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // AIR playtest mode: nothing is picked up or ejected, the user's own furni included.
        if (RoomPlayTestSupport.isRestricted(this.client.getUser())) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null) return;

        if (item instanceof InteractionPostIt) return;
        // A crackable already broken has given its reward (a subscription at once): picked up before it vanished,
        // it was kept and could be cracked again.
        if (item instanceof InteractionCrackable crackable && crackable.cracked) return;

        // A Place Furni copy: only the room owner and the owner of the box that placed it may pick it
        // up, which removes it (there is nothing to give back). It never takes a new owner.
        if (item.isTemporary()) {
            int userId = this.client.getUser().getUserInfo().getId();

            if (userId == room.getOwnerId()
                    || (item.getTemporaryOwnerId() > 0 && userId == item.getTemporaryOwnerId())) {
                room.ejectUserItem(item);
            }
            return;
        }

        if (item.getUserId() == this.client.getUser().getUserInfo().getId()) {
            if (this.askToConfirm(item, confirmed)) return;
            room.pickUpItem(item, this.client.getUser());
        } else {
            if (room.hasRights(this.client.getUser())) {
                if (this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)
                        && !WiredWebApiOwnership.isWebApiItem(item)) {
                    item.setUserId(this.client.getUser().getUserInfo().getId());
                } else if (this.client.getUser().getUserInfo().getId() != room.getOwnerId()
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
    private boolean askToConfirm(UserItem item, boolean confirmed) {
        if (confirmed || !storesVariableData(item)) return false;

        this.client.sendResponse(new ObjectRemoveConfirmComposer(
                item.getBaseItem().getType() == FurnitureType.WALL,
                item.getId(),
                "wiredfurni.variables.pickup.title",
                "wiredfurni.variables.pickup.body"));
        return true;
    }

    static boolean storesVariableData(UserItem item) {
        return (item instanceof WiredExtraUserVariable user && user.isPermanentAvailability())
                || (item instanceof WiredExtraFurniVariable furni && furni.isPermanentAvailability())
                || (item instanceof WiredExtraRoomVariable room && room.isPermanentAvailability());
    }
}
