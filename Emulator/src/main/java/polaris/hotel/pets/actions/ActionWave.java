package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionWave extends PetAction {
    public ActionWave() {
        super(PetTasks.WAVE, false);

        this.statusToSet.add(RoomUnitStatus.WAVE);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        // WAV
        if (pet.getHappiness() > 65) {
            pet.getRoomUnit().setStatus(RoomUnitStatus.WAVE, "0");

            Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.WAVE, null, false), 2000);

            // Waving is a fun trick
            pet.addHappiness(5);

            pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
            return true;
        }

        pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));
        return false;
    }
}
