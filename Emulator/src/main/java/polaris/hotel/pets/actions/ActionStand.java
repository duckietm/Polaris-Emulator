package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionStand extends PetAction {
    public ActionStand() {
        super(PetTasks.STAND, true);
        this.statusToRemove.add(RoomUnitStatus.MOVE);
        this.statusToRemove.add(RoomUnitStatus.LAY);
        this.statusToRemove.add(RoomUnitStatus.DEAD);
        this.statusToRemove.add(RoomUnitStatus.LAY);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        if (pet.getHappiness() > 30) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
