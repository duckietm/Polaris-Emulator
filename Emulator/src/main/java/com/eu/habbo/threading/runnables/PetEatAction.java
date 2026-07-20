package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetFood;
import com.eu.habbo.habbohotel.pets.GnomePet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class PetEatAction implements Runnable {
    private final Pet pet;
    private final InteractionPetFood food;

    public PetEatAction(Pet pet, InteractionPetFood food) {
        this.pet = pet;
        this.food = food;
    }

    @Override
    public void run() {
        if (this.pet.getRoomUnit() != null && this.pet.getRoom() != null) {
            // Check if food still has portions left (state < stateCount means food remaining)
            int currentState = 0;
            try {
                currentState = Integer.parseInt(this.food.getExtradata());
            } catch (NumberFormatException e) {
                currentState = 0;
            }

            if (this.pet.levelHunger >= 10
                    && this.food != null
                    && currentState < this.food.getBaseItem().getStateCount()) {
                // Say eating vocal on first bite
                if (currentState == 0 || Emulator.getRandom().nextInt(3) == 0) {
                    this.pet.say(this.pet.getPetData().randomVocal(PetVocalsType.EATING));
                }

                // Faster eating: reduce 40 hunger per bite instead of 20
                this.pet.addHunger(-40);
                this.pet.setTask(PetTasks.EAT);
                this.pet.getRoomUnit().setCanWalk(false);

                // Advance food state (each bite uses up a portion)
                this.food.setExtradata((currentState + 1) + "");
                this.pet.getRoom().updateItem(this.food);

                if (this.pet instanceof GnomePet) {
                    if (this.pet.getPetData().getType() == 26) {
                        AchievementManager.progressAchievement(
                                Emulator.getGameEnvironment().getHabboManager().getHabbo(this.pet.getUserId()),
                                Emulator.getGameEnvironment()
                                        .getAchievementManager()
                                        .getAchievement("GnomeFeeding"),
                                40);
                    } else {
                        AchievementManager.progressAchievement(
                                Emulator.getGameEnvironment().getHabboManager().getHabbo(this.pet.getUserId()),
                                Emulator.getGameEnvironment()
                                        .getAchievementManager()
                                        .getAchievement("LeprechaunFeeding"),
                                40);
                    }
                } else {
                    AchievementManager.progressAchievement(
                            Emulator.getGameEnvironment().getHabboManager().getHabbo(this.pet.getUserId()),
                            Emulator.getGameEnvironment()
                                    .getAchievementManager()
                                    .getAchievement("PetFeeding"),
                            40);
                }

                // Faster eating: 500ms between bites instead of 1000ms
                Emulator.getThreading().run(this, 500);
            } else {
                // Food is empty - remove it
                if (this.food != null && currentState >= this.food.getBaseItem().getStateCount()) {
                    var threading = Emulator.getThreading();
                    threading.run(() -> threading.runPersistence(new QueryDeleteHabboItem(this.food.getId())), 250);
                    if (this.pet.getRoom() != null) {
                        this.pet.getRoom().removeHabboItem(this.food);
                        this.pet.getRoom().sendComposer(new RemoveFloorItemComposer(this.food, true).compose());
                    }
                }

                this.pet.setTask(PetTasks.FREE);
                this.pet.getRoomUnit().removeStatus(RoomUnitStatus.EAT);
                this.pet.getRoomUnit().setCanWalk(true);
                this.pet.getRoom().sendComposer(new RoomUserStatusComposer(this.pet.getRoomUnit()).compose());
            }
        }
    }
}
