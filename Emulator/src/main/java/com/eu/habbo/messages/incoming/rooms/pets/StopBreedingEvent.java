package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class StopBreedingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (item instanceof InteractionPetBreedingNest nest) {
            // Cancelling releases both parents, so it belongs to the owners who were
            // asked to confirm - not to any visitor who can see the nest.
            int userId = this.client.getHabbo().getHabboInfo().getId();
            boolean ownsAParent = (nest.petOne != null && nest.petOne.getUserId() == userId)
                    || (nest.petTwo != null && nest.petTwo.getUserId() == userId);
            if (!ownsAParent && nest.getUserId() != userId) return;

            nest.stopBreeding(this.client.getHabbo());
        }
    }
}
