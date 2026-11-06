package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;

public class ActionSpin extends PetAction {
    public ActionSpin() {
        super(PetTasks.FREE, true);
        this.minimumActionDuration = 2000;
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        // Spin animation - rotate through all directions
        for (int i = 0; i < 8; i++) {
            final int rotation = i;
            Emulator.getThreading()
                    .run(
                            () -> {
                                pet.getRoomUnit().setRotation(RoomUserRotation.values()[rotation]);
                                pet.packetUpdate = true;
                            },
                            i * 250);
        }

        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
