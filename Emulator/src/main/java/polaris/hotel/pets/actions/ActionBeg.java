package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionBeg extends PetAction {
    public ActionBeg() {
        super(PetTasks.BEG, true);
        this.statusToSet.add(RoomUnitStatus.BEG);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        // Begging is fun interaction with owner
        pet.addHappiness(5);

        if (pet.getHappiness() > 90) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
