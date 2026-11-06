package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionRingOfFire extends PetAction {
    public ActionRingOfFire() {
        super(null, true);
        this.minimumActionDuration = 4000;
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        if (pet.getRoom() == null || pet.getRoomUnit() == null) {
            return false;
        }

        // Ring of Fire can only be performed while hanging on a tree
        if (!pet.getRoomUnit().hasStatus(RoomUnitStatus.HANG)) {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
            return false;
        }

        // Transition from HANG to RINGOFFIRE
        pet.getRoomUnit().removeStatus(RoomUnitStatus.HANG);
        pet.getRoomUnit()
                .setStatus(
                        RoomUnitStatus.RINGOFFIRE,
                        pet.getRoomUnit().getCurrentLocation().getStackHeight() + "");
        pet.packetUpdate = true;

        // After ring of fire, go back to hanging
        Emulator.getThreading()
                .run(
                        () -> {
                            pet.getRoomUnit().removeStatus(RoomUnitStatus.RINGOFFIRE);
                            pet.getRoomUnit().setStatus(RoomUnitStatus.HANG, "");
                            pet.packetUpdate = true;
                        },
                        minimumActionDuration);

        pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        return true;
    }
}
