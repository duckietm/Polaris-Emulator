package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;

public class PetFollowUser implements Runnable {
    private final int directionOffset;
    private final User user;
    private final Pet pet;

    public PetFollowUser(Pet pet, User user, int offset) {
        this.pet = pet;
        this.user = user;
        this.directionOffset = offset;
    }

    @Override
    public void run() {
        // Comprehensive null checks
        if (this.pet == null || this.pet.getRoom() == null || this.pet.getRoomUnit() == null) {
            return; // Stop following - pet or room is gone
        }

        // Check if task is any follow type
        PetTasks task = this.pet.getTask();
        if (task != PetTasks.FOLLOW && task != PetTasks.FOLLOW_LEFT && task != PetTasks.FOLLOW_RIGHT) {
            return; // Task was changed, stop
        }

        // Check if user is still valid
        if (this.user == null || this.user.getRoomUnit() == null) {
            this.pet.setTask(PetTasks.FREE);
            return; // Owner gone, stop following
        }

        // Check if user is still in the same room as the pet
        if (this.user.getUserInfo().getCurrentRoom() != this.pet.getRoom()) {
            this.pet.setTask(PetTasks.FREE);
            this.pet.say(this.pet.getPetData().randomVocal(PetVocalsType.GENERIC_SAD));
            return;
        }

        // Calculate target position
        RoomTile userTile = this.user.getRoomUnit().getCurrentLocation();
        if (userTile == null) {
            Emulator.getThreading().run(this, 500);
            return;
        }

        int targetRotation =
                Math.abs((this.user.getRoomUnit().getBodyRotation().getValue() + this.directionOffset + 4) % 8);

        RoomTile target = this.pet.getRoom().getLayout().getTileInFront(userTile, targetRotation);

        // Validate target tile - try alternative positions if needed
        if (target == null
                || target.x < 0
                || target.y < 0
                || !this.pet.getRoom().getLayout().tileWalkable(target.x, target.y)) {
            // Try directly behind user
            target = this.pet
                    .getRoom()
                    .getLayout()
                    .getTileInFront(
                            userTile, (this.user.getRoomUnit().getBodyRotation().getValue() + 4) % 8);
        }

        // Try other adjacent positions if still invalid
        if (target == null
                || target.x < 0
                || target.y < 0
                || !this.pet.getRoom().getLayout().tileWalkable(target.x, target.y)) {
            // Try to the left
            target = this.pet
                    .getRoom()
                    .getLayout()
                    .getTileInFront(
                            userTile, (this.user.getRoomUnit().getBodyRotation().getValue() + 2) % 8);
        }

        if (target == null
                || target.x < 0
                || target.y < 0
                || !this.pet.getRoom().getLayout().tileWalkable(target.x, target.y)) {
            // Try to the right
            target = this.pet
                    .getRoom()
                    .getLayout()
                    .getTileInFront(
                            userTile, (this.user.getRoomUnit().getBodyRotation().getValue() + 6) % 8);
        }

        // If we found a valid target, move there
        if (target != null && target.x >= 0 && target.y >= 0) {
            if (this.pet.getRoom().getLayout().tileWalkable(target.x, target.y)) {
                this.pet.getRoomUnit().setGoalLocation(target);
                this.pet.getRoomUnit().setCanWalk(true);
            }
        }

        // Continue following with slight randomization for natural behavior
        int nextDelay = 400 + Emulator.getRandom().nextInt(200);
        Emulator.getThreading().run(this, nextDelay);
    }
}
