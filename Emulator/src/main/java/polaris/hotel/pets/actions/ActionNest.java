package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.users.User;

public class ActionNest extends PetAction {
    public ActionNest() {
        super(null, false);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        // Pet always obeys nest command - will go to nest or lay down if no nest available
        pet.findNest();

        if (pet.getEnergy() < 30) pet.say(pet.getPetData().randomVocal(PetVocalsType.TIRED));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
