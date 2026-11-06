package polaris.messages.incoming.rooms.pets;

import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class StopBreedingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item instanceof InteractionPetBreedingNest nest) {
            // Cancelling releases both parents, so it belongs to the owners who were
            // asked to confirm - not to any visitor who can see the nest.
            int userId = this.client.getUser().getUserInfo().getId();
            boolean ownsAParent = (nest.petOne != null && nest.petOne.getUserId() == userId)
                    || (nest.petTwo != null && nest.petTwo.getUserId() == userId);
            if (!ownsAParent && nest.getUserId() != userId) return;

            nest.stopBreeding(this.client.getUser());
        }
    }
}
