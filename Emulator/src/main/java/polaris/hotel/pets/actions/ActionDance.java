package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionDance extends PetAction {
    public ActionDance() {
        super(PetTasks.FREE, true);
        this.minimumActionDuration = 5000;
        this.statusToSet.add(RoomUnitStatus.DANCE);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();
        pet.getRoomUnit().setStatus(RoomUnitStatus.DANCE, "");

        Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.DANCE, null, false), 5000);

        // Dancing is fun!
        pet.addHappiness(10);

        if (pet.getHappiness() > 60) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
