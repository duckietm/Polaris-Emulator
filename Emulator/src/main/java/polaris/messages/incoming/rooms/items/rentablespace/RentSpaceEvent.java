package polaris.messages.incoming.rooms.items.rentablespace;

import polaris.hotel.items.interactions.InteractionRentableSpace;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.rooms.items.rentablespaces.RentableSpaceRentFailedComposer;
import polaris.messages.outgoing.rooms.items.rentablespaces.RentableSpaceRentOkComposer;

/**
 * RentableSpaceRent (2946): the widget's rent button. Answers with
 * RentableSpaceRentOk (the client then re-requests the status) or
 * RentableSpaceRentFailed with the reason the widget's error view shows.
 */
public class RentSpaceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (!(item instanceof InteractionRentableSpace space)) return;

        int errorCode = space.tryRent(this.client.getUser());

        if (errorCode != 0) {
            this.client.sendResponse(new RentableSpaceRentFailedComposer(errorCode));
            return;
        }

        room.updateItem(space);
        this.client.sendResponse(new RentableSpaceRentOkComposer(space.getSecondsRemaining()));
    }
}
