package polaris.messages.incoming.rooms.pets;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.pets.MonsterplantPet;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.PetRespectFailedComposer;
import polaris.threading.runnables.RoomUnitWalkToLocation;

public class ScratchPetEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        final int petId = this.packet.readInt();

        final User user = this.client.getUser();
        if (user == null) {
            return;
        }

        final Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        final Pet pet = room.getPet(petId);
        if (pet == null) {
            return;
        }

        // Official class_1691 PetRespectFailed: an account younger than the configured minimum
        // cannot scratch, and the client shows room.error.pets.respectfailed with both numbers.
        int requiredDays = Emulator.getConfig().getInt("hotel.pets.respect.minimum_days", 0);

        if (requiredDays > 0) {
            int ageInDays = (Emulator.getIntUnixTimestamp() - user.getUserInfo().getAccountCreated()) / 86400;

            if (ageInDays < requiredDays) {
                this.client.sendResponse(new PetRespectFailedComposer(requiredDays, ageInDays));
                return;
            }
        }

        if (user.getUserStats().petRespectPointsToGive > 0 || pet instanceof MonsterplantPet) {

            List<Runnable> tasks = new ArrayList<>();
            tasks.add(() -> {
                // Checked again on arrival: the walk takes time, and a second scratch sent meanwhile used the same
                // point, so respect went below zero.
                if (user.getUserInfo().getCurrentRoom() != room || pet.getRoomUnit() == null) return;
                if (!(pet instanceof MonsterplantPet) && user.getUserStats().petRespectPointsToGive <= 0) return;

                pet.scratched(user);
                Emulator.getThreading().run(pet);
            });

            RoomTile closestTile = user.getRoomUnit()
                    .getClosestAdjacentTile(
                            pet.getRoomUnit().getX(), pet.getRoomUnit().getY(), true);
            if (closestTile != null) {
                user.getRoomUnit().setGoalLocation(closestTile);
                Emulator.getThreading()
                        // Nothing happens when the walk fails: the same tasks ran on failure too, so a pet out of
                        // reach was scratched from wherever the user stood.
                        .run(new RoomUnitWalkToLocation(
                                user.getRoomUnit(), closestTile, room, tasks, new ArrayList<>()));
            }
        }
    }
}
