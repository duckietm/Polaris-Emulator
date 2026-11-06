package polaris.messages.incoming.rooms.pets;

import polaris.hotel.pets.HorsePet;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.RideablePet;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.pets.RoomPetHorseFigureComposer;

public class PetRideSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        Pet pet = this.client.getUser().getUserInfo().getCurrentRoom().getPet(petId);

        if (pet == null
                || pet.getUserId() != this.client.getUser().getUserInfo().getId()
                || !(pet instanceof RideablePet)) return;

        RideablePet rideablePet = ((RideablePet) pet);

        rideablePet.setAnyoneCanRide(!rideablePet.anyoneCanRide());
        rideablePet.needsUpdate = true;

        if (!rideablePet.anyoneCanRide()
                && rideablePet.getRider() != null
                && rideablePet.getRider().getUserInfo().getId()
                        != this.client.getUser().getUserInfo().getId()) {
            rideablePet.getRider().getUserInfo().dismountPet();
        }

        if (pet instanceof HorsePet) {
            this.client.sendResponse(new RoomPetHorseFigureComposer((HorsePet) pet));
        }
    }
}
