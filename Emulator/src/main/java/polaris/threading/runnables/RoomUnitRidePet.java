package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserEffectComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class RoomUnitRidePet implements Runnable {
    private RideablePet pet;
    private User user;
    private RoomTile goalTile;

    public RoomUnitRidePet(RideablePet pet, User user, RoomTile goalTile) {
        this.pet = pet;
        this.user = user;
        this.goalTile = goalTile;
    }

    @Override
    public void run() {
        if (this.user == null
                || this.user.getRoomUnit() == null
                || this.pet == null
                || this.pet.getRoomUnit() == null
                || this.user.getUserInfo() == null
                || this.pet.getRoom() != this.user.getUserInfo().getCurrentRoom()
                || this.goalTile == null
                || this.user.getRoomUnit().getGoal() != this.goalTile) return;

        if (user.getRoomUnit().getCurrentLocation().distance(pet.getRoomUnit().getCurrentLocation()) <= 1) {
            if (user.getUserInfo().getRiding() != null || !pet.claimRider(user)) {
                user.getRoomUnit().stopWalking();
                return;
            }

            user.getRoomUnit().stopWalking();
            user.getUserInfo().getCurrentRoom().giveEffect(user, 77, -1);
            user.getUserInfo().setRiding(pet);
            user.getRoomUnit().setCurrentLocation(this.pet.getRoomUnit().getCurrentLocation());
            user.getRoomUnit().setPreviousLocation(this.pet.getRoomUnit().getCurrentLocation());
            user.getRoomUnit().setZ(this.pet.getRoomUnit().getZ() + 1);
            user.getRoomUnit().setPreviousLocationZ(this.pet.getRoomUnit().getZ() + 1);
            user.getRoomUnit().setRotation(this.pet.getRoomUnit().getBodyRotation());
            user.getRoomUnit().statusUpdate(true);

            // Set up the pet for riding
            pet.setTask(PetTasks.RIDE);
            pet.getRoomUnit().stopWalking();
            pet.getRoomUnit().setGoalLocation(user.getRoomUnit().getCurrentLocation());

            user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserStatusComposer(user.getRoomUnit()).compose());
            user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserEffectComposer(user.getRoomUnit()).compose());
            user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
        } else {
            pet.getRoomUnit().setWalkTimeOut(3 + Emulator.getIntUnixTimestamp());
            pet.getRoomUnit().stopWalking();
            user.getRoomUnit().setGoalLocation(goalTile);
            Emulator.getThreading().run(this, 500);
        }
    }
}
