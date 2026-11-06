package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;

public class ActionMoveForward extends PetAction {
    public ActionMoveForward() {
        super(null, true);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        if (pet.getRoom() == null || pet.getRoomUnit() == null) {
            return false;
        }

        RoomTile targetTile = pet.getRoom()
                .getLayout()
                .getTileInFront(
                        pet.getRoomUnit().getCurrentLocation(),
                        pet.getRoomUnit().getBodyRotation().getValue());

        if (targetTile != null && pet.getRoom().getLayout().tileWalkable(targetTile.x, targetTile.y)) {
            pet.getRoomUnit().setGoalLocation(targetTile);
            pet.getRoomUnit().setCanWalk(true);
            pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));
            return true;
        }

        pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
        return false;
    }
}
