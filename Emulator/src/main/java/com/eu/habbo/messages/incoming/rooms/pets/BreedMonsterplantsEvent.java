package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;

public class BreedMonsterplantsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int unknownInt = this.packet.readInt(); //Something state. 0 = initiate breeding

        if (unknownInt == 0) {
            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            if (room == null) return;

            Pet petOne = room.getPet(this.packet.readInt());
            Pet petTwo = room.getPet(this.packet.readInt());

            if (petOne == null || petTwo == null || petOne == petTwo) {
                return;
            }

            if (petOne instanceof MonsterplantPet plantOne && petTwo instanceof MonsterplantPet plantTwo) {

                // Validate both plants are breedable (fully grown, can breed, not dead)
                if (!plantOne.breedable() || !plantTwo.breedable()) {
                    return;
                }
                
                // Validate ownership - at least one plant must belong to the client
                // and the other must be publicly breedable or owned by client
                int clientId = this.client.getHabbo().getHabboInfo().getId();
                boolean ownsOne = plantOne.getUserId() == clientId;
                boolean ownsTwo = plantTwo.getUserId() == clientId;
                
                if (!ownsOne && !ownsTwo) {
                    // Client doesn't own either plant
                    return;
                }
                
                // If client doesn't own one of them, that one must be publicly breedable
                if (!ownsOne && !plantOne.isPubliclyBreedable()) {
                    return;
                }
                if (!ownsTwo && !plantTwo.isPubliclyBreedable()) {
                    return;
                }
                
                plantOne.breed(plantTwo);
            }
        }
    }
}
