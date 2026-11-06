package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionTorch extends PetAction {
    public ActionTorch() {
        super(null, true);

        this.minimumActionDuration = 1000;
        this.statusToSet.add(RoomUnitStatus.EAT);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        if (pet.getHappiness() < 30) {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
            return false;
        }

        pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        Emulator.getThreading()
                .run(new PetClearPosture(pet, RoomUnitStatus.EAT, null, false), this.minimumActionDuration);
        return true;
    }
}
