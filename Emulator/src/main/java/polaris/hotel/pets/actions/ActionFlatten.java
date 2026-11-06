package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionFlatten extends PetAction {
    public ActionFlatten() {
        super(PetTasks.DOWN, true);
        this.minimumActionDuration = 3000;
        this.statusToSet.add(RoomUnitStatus.FLAT);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();
        pet.getRoomUnit()
                .setStatus(
                        RoomUnitStatus.FLAT,
                        pet.getRoomUnit().getCurrentLocation().getStackHeight() + "");

        Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.FLAT, null, false), 3000);

        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
