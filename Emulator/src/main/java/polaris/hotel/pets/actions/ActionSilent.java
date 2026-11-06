package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionSilent extends PetAction {
    public ActionSilent() {
        super(null, false);

        this.statusToRemove.add(RoomUnitStatus.SPEAK);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.setMuted(true);
        pet.say(pet.getPetData().randomVocal(PetVocalsType.MUTED));

        return false;
    }
}
