package com.eu.habbo.habbohotel.pets.actions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetToy;
import com.eu.habbo.habbohotel.pets.*;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class ActionPlay extends PetAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionPlay.class);

    public ActionPlay() {
        super(PetTasks.PLAY, false);
        this.minimumActionDuration = 4000;
    }

    @Override
    public boolean apply(Pet pet, Habbo habbo, String[] data) {
        LOGGER.info("[ActionPlay] apply() called for pet: {}", pet.getName());
        
        // Check if pet has enough energy to play
        if (pet.getEnergy() < 25) {
            LOGGER.info("[ActionPlay] Pet too tired, energy: {}", pet.getEnergy());
            pet.say(pet.getPetData().randomVocal(PetVocalsType.TIRED));
            return false;
        }
        
        if (pet.getRoom() == null || pet.getRoom().getRoomSpecialTypes() == null) {
            LOGGER.info("[ActionPlay] Room or RoomSpecialTypes is null");
            return false;
        }
        
        // Get all pet toys in the room
        Set<InteractionPetToy> toys = pet.getRoom().getRoomSpecialTypes().getPetToys();
        LOGGER.info("[ActionPlay] Found {} pet toys in room", toys.size());
        
        // Find a toy to play with
        HabboItem toy = pet.getPetData().randomToyItem(toys);
        LOGGER.info("[ActionPlay] randomToyItem returned: {}", toy != null ? toy.getId() : "null");
        
        // If no compatible toy, just pick any toy in the room
        if (toy == null && !toys.isEmpty()) {
            for (InteractionPetToy t : toys) {
                toy = t;
                LOGGER.info("[ActionPlay] Using any toy: {}", toy.getId());
                break;
            }
        }
        
        if (toy != null) {
            RoomTile toyTile = pet.getRoom().getLayout().getTile(toy.getX(), toy.getY());
            LOGGER.info("[ActionPlay] Toy at tile: ({}, {}), tile found: {}", toy.getX(), toy.getY(), toyTile != null);
            
            if (toyTile != null) {
                pet.clearPosture();
                pet.getRoomUnit().setCanWalk(true);
                pet.setTask(PetTasks.PLAY);
                
                double distance = pet.getRoomUnit().getCurrentLocation().distance(toyTile);
                LOGGER.info("[ActionPlay] Distance to toy: {}", distance);
                
                // Check if already at the toy
                if (distance == 0) {
                    // Already at toy - start playing immediately
                    LOGGER.info("[ActionPlay] Already at toy, starting play");
                    this.startPlaying(pet, toy);
                } else {
                    // Walk to toy first
                    LOGGER.info("[ActionPlay] Setting goal location to toy");
                    pet.getRoomUnit().setGoalLocation(toyTile);
                    pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
                    // The InteractionPetToy.onWalkOn will handle the actual play when pet arrives
                }
                return true;
            }
        }
        
        LOGGER.info("[ActionPlay] No toy found, doing solo play");
        // No toy found - play solo animation
        pet.clearPosture();
        pet.setTask(PetTasks.PLAY);
        pet.getRoomUnit().setStatus(RoomUnitStatus.PLAY, "0");
        pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        pet.packetUpdate = true;
        
        // Give smaller rewards for solo play
        pet.addHappiness(10);
        pet.addEnergy(-5);
        pet.addExperience(3);
        
        Emulator.getThreading().run(() -> {
            if (pet.getRoomUnit() != null) {
                pet.getRoomUnit().removeStatus(RoomUnitStatus.PLAY);
                pet.packetUpdate = true;
            }
            pet.setTask(PetTasks.FREE);
        }, this.minimumActionDuration);
        
        return true;
    }
    
    private void startPlaying(Pet pet, HabboItem toy) {
        pet.getRoomUnit().clearStatus();
        pet.getRoomUnit().setStatus(RoomUnitStatus.PLAY, pet.getRoomUnit().getCurrentLocation().getStackHeight() + "");
        pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        pet.packetUpdate = true;
        
        // Playing with toy gives better rewards
        pet.addHappiness(25);
        pet.addEnergy(-10);
        pet.addExperience(10);
        
        // Update toy state
        toy.setExtradata("1");
        pet.getRoom().updateItemState(toy);
        
        int playDuration = 2500 + (Emulator.getRandom().nextInt(20) * 500);
        Emulator.getThreading().run(() -> {
            toy.setExtradata("0");
            pet.getRoom().updateItem(toy);
            if (pet.getRoomUnit() != null) {
                pet.getRoomUnit().removeStatus(RoomUnitStatus.PLAY);
                pet.packetUpdate = true;
            }
            pet.setTask(PetTasks.FREE);
        }, playDuration);
    }
}
