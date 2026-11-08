package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.InteractionCrackable;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class RoomPickupChooserEvent extends MessageHandler {
    private static final int MAX_PICKUP_CHOOSER_ITEMS = 100;

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();

        if (count <= 0 || count > MAX_PICKUP_CHOOSER_ITEMS) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // AIR playtest mode: nothing is picked up or ejected, the user's own furni included.
        if (RoomPlayTestSupport.isRestricted(this.client.getUser())) return;

        for (int i = 0; i < count; i++) {
            int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
            UserItem item = room.getUserItem(itemId);

            if (item != null) {
                if (item instanceof InteractionPostIt) continue;
                if (item instanceof InteractionCrackable crackable && crackable.cracked) continue;
                // A permanent variable box loses its stored values when picked up: the single pick-up asks first,
                // the chooser has no way to, so it leaves the box where it is.
                if (RoomPickupItemEvent.storesVariableData(item)) continue;

                // A Place Furni copy goes only by the hand of the room owner or of the box's owner, and never
                // changes owner: it is removed, as the single pick-up does.
                if (item.isTemporary()) {
                    int userId = this.client.getUser().getUserInfo().getId();
                    if (userId == room.getOwnerId()
                            || (item.getTemporaryOwnerId() > 0 && userId == item.getTemporaryOwnerId())) {
                        room.ejectUserItem(item);
                    }
                    continue;
                }

                if (item.getUserId() == this.client.getUser().getUserInfo().getId()) {
                    room.pickUpItem(item, this.client.getUser());
                } else {
                    if (room.hasRights(this.client.getUser())) {
                        if (this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)
                                && !WiredWebApiOwnership.isWebApiItem(item)) {
                            item.setUserId(this.client.getUser().getUserInfo().getId());
                        } else if (this.client.getUser().getUserInfo().getId() != room.getOwnerId()
                                && item.getUserId() == room.getOwnerId()) {
                            continue;
                        }

                        room.ejectUserItem(item);
                    }
                }
            }
        }
    }
}
