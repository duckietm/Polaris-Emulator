package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionStay extends PetAction {
    public ActionStay() {
        super(PetTasks.STAY, true);

        this.statusToRemove.remove(RoomUnitStatus.MOVE);
        this.statusToRemove.remove(RoomUnitStatus.DEAD);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        pet.getRoomUnit().setCanWalk(false);
        pet.setStayStartedAt(Emulator.getIntUnixTimestamp());

        // Staying still is boring
        pet.addHappiness(-5);

        pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
