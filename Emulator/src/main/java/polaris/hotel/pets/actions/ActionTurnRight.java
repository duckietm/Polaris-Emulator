package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;

public class ActionTurnRight extends PetAction {
    public ActionTurnRight() {
        super(null, true);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        pet.getRoomUnit()
                .setBodyRotation(
                        RoomUserRotation.values()[
                                (pet.getRoomUnit().getBodyRotation().getValue() + 1 > 7
                                        ? 0
                                        : pet.getRoomUnit().getBodyRotation().getValue() + 1)]);
        pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));
        return true;
    }
}
