package polaris.hotel.items.interactions.pets;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUnitType;
import polaris.threading.runnables.PetClearPosture;

public class InteractionPetTrampoline extends InteractionDefault {
    public InteractionPetTrampoline(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionPetTrampoline(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {
        // Trampolines are not clickable by users
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        this.setExtradata("0");
        room.updateItem(this);

        for (Pet pet : room.getPetsAt(oldLocation)) {
            pet.getRoomUnit().removeStatus(RoomUnitStatus.JUMP);
            pet.packetUpdate = true;
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");

        for (Pet pet : room.getPetsAt(room.getLayout().getTile(this.getX(), this.getY()))) {
            pet.getRoomUnit().removeStatus(RoomUnitStatus.JUMP);
            pet.packetUpdate = true;
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        if (pet != null
                && pet.getPetData().haveToyItem(this.getBaseItem())
                && this.getOccupyingTiles(room.getLayout())
                        .contains(pet.getRoomUnit().getGoal())) {
            if (pet.getEnergy() <= 35) {
                return;
            }

            pet.clearPosture();
            pet.setTask(PetTasks.JUMP);
            pet.getRoomUnit().setStatus(RoomUnitStatus.JUMP, "");
            pet.packetUpdate = true;

            Emulator.getThreading()
                    .run(
                            () -> {
                                new PetClearPosture(pet, RoomUnitStatus.JUMP, null, false);
                                pet.getRoomUnit().setGoalLocation(room.getRandomWalkableTile());
                                this.setExtradata("0");
                                room.updateItemState(this);
                            },
                            4000);

            pet.addHappiness(25);

            this.setExtradata("1");
            room.updateItemState(this);
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        if (pet != null) {
            this.setExtradata("0");
            room.updateItem(this);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.JUMP);
            pet.packetUpdate = true;
        }
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        Pet pet = room.getPet(roomUnit);
        return roomUnit.getRoomUnitType() == RoomUnitType.PET
                && pet != null
                && pet.getPetData().haveToyItem(this.getBaseItem());
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
