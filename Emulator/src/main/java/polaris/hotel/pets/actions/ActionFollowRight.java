package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetFollowUser;

public class ActionFollowRight extends PetAction {
    public ActionFollowRight() {
        super(PetTasks.FOLLOW_RIGHT, true);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        // Follow right.
        pet.clearPosture();

        Emulator.getThreading().run(new PetFollowUser(pet, user, -2));

        // Following owner is enjoyable
        pet.addHappiness(5);

        if (pet.getHappiness() > 75) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
