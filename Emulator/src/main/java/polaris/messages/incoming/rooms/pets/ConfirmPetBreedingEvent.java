package polaris.messages.incoming.rooms.pets;

import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class ConfirmPetBreedingEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String name = this.packet.readString();
        int petOneId = this.packet.readInt();
        int petTwoId = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item instanceof InteractionPetBreedingNest) {
            ((InteractionPetBreedingNest) item).breed(this.client.getUser(), name, petOneId, petTwoId);
        }
    }
}
