package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionTripleJump extends PetAction {
    public ActionTripleJump() {
        super(PetTasks.JUMP, true);
        this.minimumActionDuration = 4000;
        this.statusToSet.add(RoomUnitStatus.JUMP);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        // Triple jump - three jump animations
        for (int i = 0; i < 3; i++) {
            Emulator.getThreading()
                    .run(
                            () -> {
                                pet.getRoomUnit().setStatus(RoomUnitStatus.JUMP, "");
                                pet.packetUpdate = true;
                            },
                            i * 1200);
        }

        Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.JUMP, null, false), 4000);

        if (pet.getHappiness() > 70) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
