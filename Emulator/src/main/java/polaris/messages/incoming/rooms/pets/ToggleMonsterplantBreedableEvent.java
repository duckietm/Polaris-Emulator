package polaris.messages.incoming.rooms.pets;

import polaris.Emulator;
import polaris.hotel.pets.MonsterplantPet;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.pets.PetStatusUpdateComposer;

public class ToggleMonsterplantBreedableEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        Pet pet = room.getPet(petId);

        if (pet != null) {
            if (pet.getUserId() == this.client.getUser().getUserInfo().getId()) {
                if (pet instanceof MonsterplantPet) {
                    MonsterplantPet monsterplant = (MonsterplantPet) pet;

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
