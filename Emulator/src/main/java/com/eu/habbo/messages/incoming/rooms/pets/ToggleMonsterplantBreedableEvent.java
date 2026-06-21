package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.pets.PetStatusUpdateComposer;

public class ToggleMonsterplantBreedableEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        Pet pet = room.getPet(petId);

        if (pet != null) {
            if (pet.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
                if (pet instanceof MonsterplantPet monsterplant) {
                    // Only allow toggling if plant is breedable (fully grown, can breed, not dead)
                    if (monsterplant.breedable()) {
                        // Toggle the publicly breedable state (was previously setting to same value - bug fix)
                        monsterplant.setPubliclyBreedable(!monsterplant.isPubliclyBreedable());
                        
                        // Mark for database update
                        monsterplant.needsUpdate = true;
                        Emulator.getThreading().run(monsterplant);
                        
                        // Send status update to room
                        room.sendComposer(new PetStatusUpdateComposer(monsterplant).compose());
                    }
                }
            }
        }
    }
}