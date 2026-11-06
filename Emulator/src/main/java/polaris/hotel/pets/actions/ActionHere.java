package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class ActionHere extends PetAction {
    public ActionHere() {
        super(PetTasks.HERE, false);

        this.statusToRemove.add(RoomUnitStatus.DEAD);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        if (pet.getRoom() == null || user.getRoomUnit() == null) {
            return false;
        }

        pet.clearPosture();

        // Try tile in front of user first
        RoomTile target = pet.getRoom()
                .getLayout()
                .getTileInFront(
                        user.getRoomUnit().getCurrentLocation(),
                        user.getRoomUnit().getBodyRotation().getValue());

        // If not walkable, try user's current tile
        if (target == null || !pet.getRoom().getLayout().tileWalkable(target.x, target.y)) {
            target = user.getRoomUnit().getCurrentLocation();
        }

        if (target != null) {
            pet.getRoomUnit().setGoalLocation(target);
            pet.getRoomUnit().setCanWalk(true);
        }

        if (pet.getHappiness() > 75) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
