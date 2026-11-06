package polaris.plugin.events.pets;

import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.RoomChatMessage;

public class PetTalkEvent extends PetEvent {

    public RoomChatMessage message;

    public PetTalkEvent(Pet pet, RoomChatMessage message) {
        super(pet);

        this.message = message;
    }
}
