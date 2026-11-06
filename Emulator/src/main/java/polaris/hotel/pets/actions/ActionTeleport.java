package polaris.hotel.pets.actions;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;

public class ActionTeleport extends PetAction {
    public ActionTeleport() {
        super(PetTasks.FREE, true);
        this.minimumActionDuration = 1000;
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        // Teleport pet to a random walkable tile near owner
        if (user != null && user.getRoomUnit() != null && pet.getRoom() != null) {
            RoomTile targetTile = pet.getRoom()
                    .getLayout()
                    .getTileInFront(
                            user.getRoomUnit().getCurrentLocation(),
                            user.getRoomUnit().getBodyRotation().getValue());

            if (targetTile != null && targetTile.isWalkable()) {
                pet.getRoomUnit().setLocation(targetTile);
                pet.getRoomUnit().setZ(targetTile.getStackHeight());
                pet.packetUpdate = true;
            }
        }

        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
