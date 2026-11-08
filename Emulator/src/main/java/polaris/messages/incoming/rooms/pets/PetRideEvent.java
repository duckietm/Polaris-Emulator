package polaris.messages.incoming.rooms.pets;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.threading.runnables.RoomUnitRidePet;

public class PetRideEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();
        this.packet.readBoolean(); // Desired mount state; legacy behavior toggles from the authoritative room state.
        User user = this.client.getUser();
        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) return;

        Pet pet = room.getPet(petId);

        if (!(pet instanceof RideablePet)) return;

        RideablePet rideablePet = (RideablePet) pet;

        // dismount
        if (user.getUserInfo().getRiding() != null) {
            user.getUserInfo().dismountPet();
            return;
        }

        // someone is already on it, or there is no saddle to sit on
        if (rideablePet.getRider() != null || !rideablePet.hasSaddle()) return;

        // check if able to ride
        if (!rideablePet.anyoneCanRide() && user.getUserInfo().getId() != rideablePet.getUserId()) return;

        List<RoomTile> availableTiles =
                room.getLayout().getWalkableTilesAround(pet.getRoomUnit().getCurrentLocation());

        // if cant reach it then cancel
        if (availableTiles.isEmpty()) return;

        RoomTile goalTile = availableTiles.get(0);
        user.getRoomUnit().setGoalLocation(goalTile);
        Emulator.getThreading().run(new RoomUnitRidePet(rideablePet, user, goalTile));
        rideablePet.getRoomUnit().setWalkTimeOut(3 + Emulator.getIntUnixTimestamp());
        rideablePet.getRoomUnit().stopWalking();
    }
}
