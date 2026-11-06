package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionDown extends PetAction {
    public ActionDown() {
        super(PetTasks.DOWN, true);
        this.minimumActionDuration = 4000;
        this.statusToRemove.add(RoomUnitStatus.BEG);
        this.statusToRemove.add(RoomUnitStatus.MOVE);
        this.statusToRemove.add(RoomUnitStatus.DEAD);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        if (pet.getTask() != PetTasks.DOWN && !pet.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) {
            pet.getRoomUnit().cmdLay = true;
            pet.getRoomUnit()
                    .setStatus(
                            RoomUnitStatus.LAY,
                            pet.getRoomUnit().getCurrentLocation().getStackHeight() + "");

            // Lying down is a bit boring but restful
            pet.addHappiness(-2);

            Emulator.getThreading()
                    .run(
                            () -> {
                                pet.getRoomUnit().cmdLay = false;
                                pet.clearPosture();
                            },
                            this.minimumActionDuration);

            if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
            else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

            return true;
        }

        return true;
    }
}
