package polaris.hotel.pets;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class PetCommand implements Comparable<PetCommand> {

    public final int id;

    public final String key;

    public final int level;

    public final int xp;

    public final int energyCost;

    public final int happinessCost;

    public final PetAction action;

    public PetCommand(ResultSet set, PetAction action) throws SQLException {
        this.id = set.getInt("command_id");
        this.key = set.getString("text");
        this.level = set.getInt("required_level");
        this.xp = set.getInt("reward_xp");
        this.energyCost = set.getInt("cost_energy");
        this.happinessCost = set.getInt("cost_happiness");
        this.action = action;
    }

    @Override
    public int compareTo(PetCommand o) {
        return this.level - o.level;
    }

    public void handle(Pet pet, User user, String[] data) {
        // Check command cooldown to prevent spam (global cooldown for ALL commands)
        if (!pet.canExecuteCommand(this.id)) {
            // Pet ignores spammed commands - maybe give a tired/annoyed response occasionally
            if (pet.getSameCommandCount() > Emulator.getConfig().getInt("pet.command.max_same_spam", 3)) {
                if (Emulator.getRandom().nextInt(3) == 0) {
                    pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
                }
            }
            return;
        }

        // Check if pet has enough energy to perform the command
        int minEnergy = Emulator.getConfig().getInt("pet.command.min_energy", 15);
        if (pet.getEnergy() < minEnergy || pet.getEnergy() < this.energyCost) {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.TIRED));
            pet.recordCommandExecution(this.id);
            return;
        }

        // Check if pet is too unhappy to obey
        int minHappiness = Emulator.getConfig().getInt("pet.command.min_happiness", 10);
        if (pet.getHappiness() < minHappiness) {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_SAD));
            pet.recordCommandExecution(this.id);
            return;
        }

        // Improved obedience formula - configurable base chance with level scaling
        int levelDifference = pet.getLevel() - this.level;
        int baseChance = Emulator.getConfig().getInt("pet.command.base_obey_chance", 70); // 70% base
        int levelBonus = Math.max(0, levelDifference * 5); // +5% per level above requirement
        int obeyChance = Math.min(95, baseChance + levelBonus); // Cap at 95%

        if (Emulator.getRandom().nextInt(100) >= obeyChance) {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
            // Don't record execution on random disobey — player can retry immediately
            return;
        }

        if (this.action != null) {
            // Allow repeating actions - removed the task comparison check
            if (this.action.stopsPetWalking) {
                pet.getRoomUnit().setGoalLocation(pet.getRoomUnit().getCurrentLocation());
            }
            if (this.action.apply(pet, user, data)) {
                // Set the pet's task from the action
                if (this.action.petTask != null) {
                    pet.setTask(this.action.petTask);
                }

                for (RoomUnitStatus status : this.action.statusToRemove) {
                    pet.getRoomUnit().removeStatus(status);
                }

                for (RoomUnitStatus status : this.action.statusToSet) {
                    pet.getRoomUnit().setStatus(status, "0");
                }

                pet.getRoomUnit().setStatus(RoomUnitStatus.GESTURE, this.action.gestureToSet);

                pet.addEnergy(-this.energyCost);
                pet.addHappiness(-this.happinessCost);
                pet.addExperience(this.xp);

                // Mark pet for status update so clients see the animation
                pet.packetUpdate = true;

                // Record successful command execution
                pet.recordCommandExecution(this.id);
            }
        }
    }
}
