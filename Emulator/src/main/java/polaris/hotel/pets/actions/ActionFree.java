package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.users.User;

public class ActionFree extends PetAction {
    public ActionFree() {
        super(PetTasks.FREE, false);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.freeCommand();

        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_HAPPY));

        return true;
    }
}
