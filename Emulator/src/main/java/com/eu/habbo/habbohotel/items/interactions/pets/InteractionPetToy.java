package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.threading.runnables.PetClearPosture;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionPetToy extends InteractionDefault {
    public InteractionPetToy(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionPetToy(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {
        // Toys are not clickable by users
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        this.setExtradata("0");
        room.updateItem(this);

        for (Pet pet : room.getPetsAt(oldLocation)) {
            pet.getRoomUnit().clearStatus();
            pet.packetUpdate = true;
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");

        for (RoomTile tile : this.getOccupyingTiles(room.getLayout())) {
            for (Pet pet : room.getPetsAt(tile)) {
                pet.getRoomUnit().clearStatus();
                pet.packetUpdate = true;
            }
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        if (pet != null && pet.getPetData().haveToyItem(this.getBaseItem())) {
            if (pet.getEnergy() <= 35) {
                return;
            }

            pet.setTask(PetTasks.PLAY);
            pet.getRoomUnit().setGoalLocation(room.getLayout().getTile(this.getX(), this.getY()));
            pet.getRoomUnit().setRotation(RoomUserRotation.values()[this.getRotation()]);
            pet.getRoomUnit().clearStatus();
            pet.getRoomUnit()
                    .setStatus(
                            RoomUnitStatus.PLAY,
                            pet.getRoomUnit().getCurrentLocation().getStackHeight() + "");
            pet.packetUpdate = true;

            // Say playful vocal
            pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));

            HabboItem item = this;
            Emulator.getThreading()
                    .run(
                            () -> {
                                pet.addHappiness(25);
                                item.setExtradata("0");
                                room.updateItem(item);
                                pet.getRoomUnit().clearStatus();
                                new PetClearPosture(pet, RoomUnitStatus.PLAY, null, true).run();
                                pet.packetUpdate = true;
                            },
                            2500 + (Emulator.getRandom().nextInt(20) * 500));
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
            pet.getRoomUnit().clearStatus();
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
