package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.pets.HorsePet;
import polaris.hotel.pets.HorseSaddle;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomPetHorseFigureComposer extends MessageComposer {
    private final HorsePet pet;

    public RoomPetHorseFigureComposer(HorsePet pet) {
        this.pet = pet;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomPetHorseFigureComposer);
        this.response.appendInt(this.pet.getRoomUnit().getId());
        this.response.appendInt(this.pet.getId());
        this.response.appendInt(this.pet.getPetData().getType());
        this.response.appendInt(this.pet.getRace());
        this.response.appendString(this.pet.getColor().toLowerCase());

        // The parts in the order of the figure text: both hair layers, then the saddle the horse wears.
        if (this.pet.hasSaddle()) {
            this.response.appendInt(2);
            this.response.appendInt(3);
            this.response.appendInt(2);
            this.response.appendInt(this.pet.getHairStyle());
            this.response.appendInt(this.pet.getHairColor());
            this.response.appendInt(3);
            this.response.appendInt(this.pet.getHairStyle());
            this.response.appendInt(this.pet.getHairColor());
            this.response.appendInt(HorseSaddle.LAYER);
            this.response.appendInt(this.pet.saddle().partId());
            this.response.appendInt(this.pet.saddle().paletteId());
        } else {
            this.response.appendInt(1);
            this.response.appendInt(2);
            this.response.appendInt(2);
            this.response.appendInt(this.pet.getHairStyle());
            this.response.appendInt(this.pet.getHairColor());
            this.response.appendInt(3);
            this.response.appendInt(this.pet.getHairStyle());
            this.response.appendInt(this.pet.getHairColor());
        }
        this.response.appendBoolean(this.pet.hasSaddle());
        this.response.appendBoolean(this.pet.anyoneCanRide());
        return this.response;
    }

    public HorsePet getPet() {
        return pet;
    }
}
