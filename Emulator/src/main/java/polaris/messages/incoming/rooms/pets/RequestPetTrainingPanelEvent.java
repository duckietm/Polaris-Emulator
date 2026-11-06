package polaris.messages.incoming.rooms.pets;

import polaris.hotel.pets.Pet;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.pets.PetTrainingPanelComposer;

public class RequestPetTrainingPanelEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        Pet pet = this.client.getUser().getUserInfo().getCurrentRoom().getPet(petId);

        if (pet != null) this.client.sendResponse(new PetTrainingPanelComposer(pet));
    }
}
