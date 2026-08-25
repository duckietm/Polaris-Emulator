package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.runnables.PetEatAction;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionPetFood extends InteractionDefault {
    public InteractionPetFood(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionPetFood(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.getExtradata().length() == 0)
            this.setExtradata("0");

        // Check if there's food left (state < stateCount means food remaining)
        int currentState = 0;
        try {
            currentState = Integer.parseInt(this.getExtradata());
        } catch (NumberFormatException e) {
            currentState = 0;
        }
        
        // If food is empty (state >= max states), don't allow eating
        if (currentState >= this.getBaseItem().getStateCount()) {
            return;
        }

        Pet pet = room.getPet(roomUnit);

        if (pet != null) {
            // Don't let ridden pets eat
            if (pet instanceof RideablePet && ((RideablePet) pet).getRider() != null)
                return;

            if (pet.getPetData().haveFoodItem(this)) {
                if (pet.levelHunger >= 35) {
                    pet.setTask(PetTasks.EAT);
                    pet.getRoomUnit().setGoalLocation(room.getLayout().getTile(this.getX(), this.getY()));
                    pet.getRoomUnit().setRotation(RoomUserRotation.values()[this.getRotation()]);
                    pet.getRoomUnit().clearStatus();
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
                    pet.getRoomUnit().setStatus(RoomUnitStatus.EAT, "0");
                    room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
                    Emulator.getThreading().run(new PetEatAction(pet, this));
                }
            }
        }
    }


    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
