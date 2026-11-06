package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionJump extends PetAction {
    public ActionJump() {
        super(PetTasks.JUMP, true);
        this.minimumActionDuration = 2000;
        this.statusToSet.add(RoomUnitStatus.JUMP);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.JUMP, null, false), 2000);

        // Jumping is fun!
        pet.addHappiness(8);

        if (pet.getHappiness() > 60) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
