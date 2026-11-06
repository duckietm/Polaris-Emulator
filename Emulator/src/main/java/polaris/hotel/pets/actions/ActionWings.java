package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionWings extends PetAction {
    public ActionWings() {
        super(null, true);

        this.statusToSet.add(RoomUnitStatus.WINGS);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        Emulator.getThreading()
                .run(new PetClearPosture(pet, RoomUnitStatus.WINGS, null, false), this.minimumActionDuration);

        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));

        return true;
    }
}
