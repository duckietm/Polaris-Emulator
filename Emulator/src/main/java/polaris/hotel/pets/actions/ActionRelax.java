package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionRelax extends PetAction {
    public ActionRelax() {
        super(null, true);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        // Relax
        if (pet.getHappiness() > 75) pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_HAPPY));
        else if (pet.getHappiness() < 30) pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_SAD));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        pet.getRoomUnit().setStatus(RoomUnitStatus.RELAX, "0");

        return true;
    }
}
