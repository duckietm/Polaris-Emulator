package polaris.messages.incoming.rooms.pets;

import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.pets.PetInformationComposer;

public class RequestPetInformationEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        Pet pet = room.getPet(petId);

        if (pet != null) {
            this.client.sendResponse(new PetInformationComposer(pet, room, this.client.getUser()));
        }
    }
}
