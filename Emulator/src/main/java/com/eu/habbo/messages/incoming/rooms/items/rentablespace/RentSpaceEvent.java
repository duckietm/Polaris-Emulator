package com.eu.habbo.messages.incoming.rooms.items.rentablespace;

import com.eu.habbo.habbohotel.items.interactions.InteractionRentableSpace;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;
import com.eu.habbo.messages.outgoing.rooms.items.rentablespaces.RentableSpaceRentFailedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.rentablespaces.RentableSpaceRentOkComposer;

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

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (!(item instanceof InteractionRentableSpace space)) return;

        int errorCode = space.tryRent(this.client.getHabbo());

        if (errorCode != 0) {
            this.client.sendResponse(new RentableSpaceRentFailedComposer(errorCode));
            return;
        }

        room.updateItem(space);
        this.client.sendResponse(new RentableSpaceRentOkComposer(space.getSecondsRemaining()));
    }
}
