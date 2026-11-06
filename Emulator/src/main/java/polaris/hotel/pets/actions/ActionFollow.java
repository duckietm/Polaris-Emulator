package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.threading.runnables.PetFollowUser;

public class ActionFollow extends PetAction {
    public ActionFollow() {
        super(PetTasks.FOLLOW, true);
        this.statusToRemove.add(RoomUnitStatus.MOVE);
        this.statusToRemove.add(RoomUnitStatus.LAY);
        this.statusToRemove.add(RoomUnitStatus.DEAD);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        Emulator.getThreading().run(new PetFollowUser(pet, user, 0));

        // Following owner is enjoyable
        pet.addHappiness(5);

        if (pet.getHappiness() > 75) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
