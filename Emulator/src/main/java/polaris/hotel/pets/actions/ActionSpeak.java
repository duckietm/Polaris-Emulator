package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetClearPosture;

public class ActionSpeak extends PetAction {
    public ActionSpeak() {
        super(PetTasks.SPEAK, false);

        this.statusToSet.add(RoomUnitStatus.SPEAK);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.setMuted(false);
        Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.SPEAK, null, false), 2000);

        // Speaking/expressing itself makes pet happy
        pet.addHappiness(3);

        if (pet.getHappiness() > 70) pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_HAPPY));
        else if (pet.getHappiness() < 30) pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_SAD));
        else if (pet.getLevelHunger() > 65) pet.say(pet.getPetData().randomVocal(PetVocalsType.HUNGRY));
        else if (pet.getLevelThirst() > 65) pet.say(pet.getPetData().randomVocal(PetVocalsType.THIRSTY));
        else if (pet.getEnergy() < 25) pet.say(pet.getPetData().randomVocal(PetVocalsType.TIRED));
        else if (pet.getTask() == PetTasks.NEST || pet.getTask() == PetTasks.DOWN)
            pet.say(pet.getPetData().randomVocal(PetVocalsType.SLEEPING));

        return true;
    }
}
