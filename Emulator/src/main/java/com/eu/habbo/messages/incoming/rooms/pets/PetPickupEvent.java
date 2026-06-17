package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddPetComposer;

public class PetPickupEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        Pet pet = room.getPet(petId);

        if (pet != null) {
            if (this.client.getHabbo().getHabboInfo().getId() == pet.getUserId() || room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
                if (!this.client.getHabbo().hasPermission(Permission.ACC_UNLIMITED_PETS) && this.client.getHabbo().getInventory().getPetsComponent().getPets().size() >= PetManager.MAXIMUM_PET_INVENTORY_SIZE) {
                    this.client.getHabbo().alert(Emulator.getTexts().getValue("error.pets.max.inventory").replace("%amount%", PetManager.MAXIMUM_PET_INVENTORY_SIZE + ""));
                    return;
                }

                if (pet instanceof RideablePet) {
                    RideablePet rideablePet = (RideablePet) pet;
                    if (rideablePet.getRider() != null) {
                        rideablePet.getRider().getHabboInfo().dismountPet(true);
                    }
                }

                pet.removeFromRoom();
                pet.run();  // Run synchronously to ensure DB is updated before returning pet to inventory

                if (this.client.getHabbo().getHabboInfo().getId() == pet.getUserId()) {
                    this.client.sendResponse(new AddPetComposer(pet));
                    this.client.getHabbo().getInventory().getPetsComponent().addPet(pet);
                } else {
                    Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(pet.getUserId());

                    if (habbo != null) {
                        habbo.getClient().sendResponse(new AddPetComposer(pet));
                        habbo.getInventory().getPetsComponent().addPet(pet);
                    }
                }
            }
        }
    }
}
