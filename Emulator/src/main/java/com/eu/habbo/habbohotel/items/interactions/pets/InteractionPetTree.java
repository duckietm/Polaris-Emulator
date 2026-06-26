package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.rooms.*;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interaction for pet trees (dragon tree, monkey tree, etc.)
 * Pets can hang from these and perform special actions like Ring of Fire.
 */
public class InteractionPetTree extends InteractionDefault {
    public InteractionPetTree(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionPetTree(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {
        // Trees are not clickable by users
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        this.setExtradata("0");
        room.updateItem(this);

        for (Pet pet : room.getPetsAt(oldLocation)) {
            pet.getRoomUnit().removeStatus(RoomUnitStatus.HANG);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.SWING);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.FLAME);
            pet.packetUpdate = true;
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");

        for (Pet pet : room.getPetsAt(room.getLayout().getTile(this.getX(), this.getY()))) {
            pet.getRoomUnit().removeStatus(RoomUnitStatus.HANG);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.SWING);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.FLAME);
            pet.packetUpdate = true;
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        // Only dragons (type 12) can use the tree
        if (pet != null && pet.getPetData().getType() == 12 && this.getOccupyingTiles(room.getLayout()).contains(pet.getRoomUnit().getGoal())) {
            if (pet.getEnergy() <= 35) {
                return;
            }

            RoomUnitStatus task = RoomUnitStatus.HANG;
            switch (pet.getTask()) {
                case RING_OF_FIRE -> task = RoomUnitStatus.RINGOFFIRE;
                case SWING -> task = RoomUnitStatus.SWING;
                default -> {
                    // Default to HANG for all other tasks
                }
            }

            // Pet arrived at tree - set hang status
            pet.setTask(PetTasks.FREE);
            pet.getRoomUnit().setGoalLocation(room.getLayout().getTile(this.getX(), this.getY()));
            pet.getRoomUnit().setRotation(RoomUserRotation.values()[this.getRotation()]);
            pet.getRoomUnit().clearStatus();
            pet.getRoomUnit().setStatus(task, "");
            pet.packetUpdate = true;
            
            // Say playful vocal
            pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
            
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
            pet.getRoomUnit().removeStatus(RoomUnitStatus.HANG);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.SWING);
            pet.getRoomUnit().removeStatus(RoomUnitStatus.FLAME);
            pet.packetUpdate = true;
        }
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        Pet pet = room.getPet(roomUnit);
        return roomUnit.getRoomUnitType() == RoomUnitType.PET && pet != null;
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
