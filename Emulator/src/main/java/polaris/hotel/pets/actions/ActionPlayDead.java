package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionPlayDead extends PetAction {
    public ActionPlayDead() {
        super(PetTasks.PLAY_DEAD, true);
        this.statusToRemove.add(RoomUnitStatus.MOVE);
        this.statusToRemove.add(RoomUnitStatus.LAY);
        this.statusToRemove.add(RoomUnitStatus.DEAD);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.clearPosture();

        pet.getRoomUnit()
                .setStatus(
                        RoomUnitStatus.DEAD,
                        pet.getRoom()
                                        .getStackHeight(
                                                pet.getRoomUnit().getX(),
                                                pet.getRoomUnit().getY(),
                                                false) + "");

        // Playing dead is not very fun
        pet.addHappiness(-3);

        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
