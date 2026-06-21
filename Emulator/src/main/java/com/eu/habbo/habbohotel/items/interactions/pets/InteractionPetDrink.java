package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.threading.runnables.PetClearPosture;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InteractionPetDrink extends InteractionDefault {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionPetDrink.class);

    public InteractionPetDrink(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionPetDrink(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canToggle(Habbo habbo, Room room) {
        return RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()), habbo.getRoomUnit().getCurrentLocation());
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null)
            return;

        if (!this.canToggle(client.getHabbo(), room)) {
            RoomTile closestTile = null;
            for (RoomTile tile : room.getLayout().getTilesAround(room.getLayout().getTile(this.getX(), this.getY()))) {
                if (tile.isWalkable() && (closestTile == null || closestTile.distance(client.getHabbo().getRoomUnit().getCurrentLocation()) > tile.distance(client.getHabbo().getRoomUnit().getCurrentLocation()))) {
                    closestTile = tile;
                }
            }

            if (closestTile != null && !closestTile.equals(client.getHabbo().getRoomUnit().getCurrentLocation())) {
                List<Runnable> onSuccess = new ArrayList<>();
                onSuccess.add(() -> {
                    this.change(room, this.getBaseItem().getStateCount() - 1);
                });

                client.getHabbo().getRoomUnit().setGoalLocation(closestTile);
                Emulator.getThreading().run(new RoomUnitWalkToLocation(client.getHabbo().getRoomUnit(), closestTile, room, onSuccess, new ArrayList<>()));
            }
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.getExtradata() == null || this.getExtradata().isEmpty())
            this.setExtradata("0");

        // Check if there's water left (state 0 = full, higher = less water)
        int currentState = 0;
        try {
            currentState = Integer.parseInt(this.getExtradata());
        } catch (NumberFormatException e) {
            currentState = 0;
        }
        
        // If water bowl is empty (state >= max states), don't allow drinking
        if (currentState >= this.getBaseItem().getStateCount() - 1) {
            return;
        }

        Pet pet = room.getPet(roomUnit);

        if (pet != null && !(pet instanceof RideablePet ridePet && ridePet.getRider() != null)
                && pet.getPetData().haveDrinkItem(this) && pet.levelThirst >= 35) {
            pet.clearPosture();
            pet.getRoomUnit().setGoalLocation(room.getLayout().getTile(this.getX(), this.getY()));
            pet.getRoomUnit().setRotation(RoomUserRotation.values()[this.getRotation()]);
            pet.getRoomUnit().clearStatus();
            pet.getRoomUnit().setStatus(RoomUnitStatus.EAT, pet.getRoomUnit().getCurrentLocation().getStackHeight() + "");
            pet.packetUpdate = true;
            
            // Say drinking vocal
            pet.say(pet.getPetData().randomVocal(PetVocalsType.DRINKING));

            // Faster drinking - 500ms instead of 1000ms
            Emulator.getThreading().run(() -> {
                pet.addThirst(-75);
                // Increase state to show less water (+1, not -1)
                this.change(room, 1);
                pet.getRoomUnit().clearStatus();
                Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.EAT, null, true), 0);
                pet.packetUpdate = true;
            }, 500);

            AchievementManager.progressAchievement(Emulator.getGameEnvironment().getHabboManager().getHabbo(pet.getUserId()), Emulator.getGameEnvironment().getAchievementManager().getAchievement("PetFeeding"), 75);
        }
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    private void change(Room room, int amount) {
        int state = 0;

        if (this.getExtradata() == null || this.getExtradata().isEmpty()) {
            this.setExtradata("0");
        }

        try {
            state = Integer.parseInt(this.getExtradata());
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        state += amount;
        if (state > this.getBaseItem().getStateCount() - 1) {
            state = this.getBaseItem().getStateCount() - 1;
        }

        if (state < 0) {
            state = 0;
        }

        this.setExtradata(state + "");
        this.needsUpdate(true);
        room.updateItemState(this);
    }
}
