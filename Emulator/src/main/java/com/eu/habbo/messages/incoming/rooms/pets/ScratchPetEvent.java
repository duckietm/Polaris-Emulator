package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.PetRespectFailedComposer;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import java.util.ArrayList;
import java.util.List;

public class ScratchPetEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        final int petId = this.packet.readInt();

        final Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        final Room room = habbo.getHabboInfo().getCurrentRoom();
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
            int ageInDays =
                    (Emulator.getIntUnixTimestamp() - habbo.getHabboInfo().getAccountCreated()) / 86400;

            if (ageInDays < requiredDays) {
                this.client.sendResponse(new PetRespectFailedComposer(requiredDays, ageInDays));
                return;
            }
        }

        if (habbo.getHabboStats().petRespectPointsToGive > 0 || pet instanceof MonsterplantPet) {

            List<Runnable> tasks = new ArrayList<>();
            tasks.add(() -> {
                pet.scratched(habbo);
                Emulator.getThreading().run(pet);
            });

            RoomTile closestTile = habbo.getRoomUnit()
                    .getClosestAdjacentTile(
                            pet.getRoomUnit().getX(), pet.getRoomUnit().getY(), true);
            if (closestTile != null) {
                habbo.getRoomUnit().setGoalLocation(closestTile);
                Emulator.getThreading()
                        .run(new RoomUnitWalkToLocation(habbo.getRoomUnit(), closestTile, room, tasks, tasks));
            }
        }
    }
}
