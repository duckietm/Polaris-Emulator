package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.users.User;

public class ActionCount extends PetAction {
    public ActionCount() {
        super(PetTasks.FREE, true);
        this.minimumActionDuration = 3000;
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        // Count by speaking
        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
