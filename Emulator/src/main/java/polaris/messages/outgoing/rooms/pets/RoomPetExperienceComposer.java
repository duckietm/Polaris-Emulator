package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.pets.Pet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomPetExperienceComposer extends MessageComposer {
    private final Pet pet;
    private final int amount;

    public RoomPetExperienceComposer(Pet pet, int amount) {
        this.pet = pet;
        this.amount = amount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomPetExperienceComposer);
        this.response.appendInt(this.pet.getId());
        this.response.appendInt(this.pet.getRoomUnit().getId());
        this.response.appendInt(this.amount);
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }

    public int getAmount() {
        return amount;
    }
}
