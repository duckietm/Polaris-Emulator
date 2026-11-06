package polaris.hotel.pets;

import polaris.Emulator;

/**
 * Manages all stat-related logic for pets including decay rates, recovery rates,
 * and mood calculations. This centralizes stat management for better maintainability.
 */
public class PetStatsManager {
    private final Pet pet;

    // Configurable decay rates
    private int hungerDecayRate;
    private int thirstDecayRate;
    private int energyDecayRate;
    private int happinessDecayRate;

    // Configurable recovery rates
    private int energyRecoveryRate;
    private int happinessRecoveryRate;

    // Configurable thresholds
    private int hungryThreshold;
    private int thirstyThreshold;
    private int tiredThreshold;
    private int sadThreshold;

    public PetStatsManager(Pet pet) {
        this.pet = pet;
        this.loadConfig();
    }

    /**
     * Loads configuration values from the emulator config.
     */
    private void loadConfig() {
        this.hungerDecayRate = Emulator.getConfig().getInt("pet.stats.hunger_decay", 1);
        this.thirstDecayRate = Emulator.getConfig().getInt("pet.stats.thirst_decay", 1);
        this.energyDecayRate = Emulator.getConfig().getInt("pet.stats.energy_decay", 1);
        this.happinessDecayRate = Emulator.getConfig().getInt("pet.stats.happiness_decay", 1);
        this.energyRecoveryRate = Emulator.getConfig().getInt("pet.stats.energy_recovery", 5);
        this.happinessRecoveryRate = Emulator.getConfig().getInt("pet.stats.happiness_recovery", 1);

        this.hungryThreshold = Emulator.getConfig().getInt("pet.threshold.hungry", 50);
        this.thirstyThreshold = Emulator.getConfig().getInt("pet.threshold.thirsty", 50);
        this.tiredThreshold = Emulator.getConfig().getInt("pet.threshold.tired", 30);
        this.sadThreshold = Emulator.getConfig().getInt("pet.threshold.sad", 30);
    }

    /**
     * Process stat changes when pet is walking/active.
     */
    public void processActiveTick() {
        this.pet.addHunger(this.hungerDecayRate);
        this.pet.addThirst(this.thirstDecayRate);
        this.pet.addEnergy(-this.energyDecayRate);
    }

    /**
     * Process stat changes when pet is in nest/down (resting).
     */
    public void processRestingTick() {
        this.pet.addHunger(-1);
        this.pet.addThirst(-1);
        this.pet.addEnergy(this.energyRecoveryRate);
        this.pet.addHappiness(this.happinessRecoveryRate);
    }

    /**
     * Process stat changes when pet is standing still/idle.
     */
    public void processIdleTick() {
        this.pet.addHunger(this.hungerDecayRate / 2);
        this.pet.addThirst(this.thirstDecayRate / 2);
    }

    /**
     * Gets the current mood of the pet based on its stats.
     * @return The current PetMood
     */
    public PetMood getCurrentMood() {
        if (this.pet.getEnergy() < 20) return PetMood.EXHAUSTED;
        if (this.pet.getLevelHunger() > 80) return PetMood.STARVING;
        if (this.pet.getLevelThirst() > 80) return PetMood.PARCHED;
        if (this.pet.getHappiness() < 20) return PetMood.DEPRESSED;
        if (this.pet.getHappiness() > 80 && this.pet.getEnergy() > 60) return PetMood.ECSTATIC;
        if (this.pet.getHappiness() > 50) return PetMood.HAPPY;
        return PetMood.NEUTRAL;
    }

    /**
     * Checks if the pet needs food.
     * @return true if hunger level exceeds the hungry threshold
     */
    public boolean needsFood() {
        return this.pet.getLevelHunger() > this.hungryThreshold;
    }

    /**
     * Checks if the pet needs water.
     * @return true if thirst level exceeds the thirsty threshold
     */
    public boolean needsWater() {
        return this.pet.getLevelThirst() > this.thirstyThreshold;
    }

    /**
     * Checks if the pet needs rest.
     * @return true if energy level is below the tired threshold
     */
    public boolean needsRest() {
        return this.pet.getEnergy() < this.tiredThreshold;
    }

    /**
     * Checks if the pet needs attention/play.
     * @return true if happiness level is below the sad threshold
     */
    public boolean needsAttention() {
        return this.pet.getHappiness() < this.sadThreshold;
    }

    /**
     * Gets the overall health score of the pet (0-100).
     * @return An integer representing overall pet health
     */
    public int getOverallHealth() {
        int maxEnergy = PetManager.maxEnergy(this.pet.getLevel());
        int energyPercent = (this.pet.getEnergy() * 100) / maxEnergy;
        int hungerPercent = 100 - this.pet.getLevelHunger();
        int thirstPercent = 100 - this.pet.getLevelThirst();
        int happinessPercent = this.pet.getHappiness();

        return (energyPercent + hungerPercent + thirstPercent + happinessPercent) / 4;
    }

    // Getters for decay/recovery rates
    public int getHungerDecayRate() {
        return hungerDecayRate;
    }

    public int getThirstDecayRate() {
        return thirstDecayRate;
    }

    public int getEnergyDecayRate() {
        return energyDecayRate;
    }

    public int getHappinessDecayRate() {
        return happinessDecayRate;
    }

    public int getEnergyRecoveryRate() {
        return energyRecoveryRate;
    }

    public int getHappinessRecoveryRate() {
        return happinessRecoveryRate;
    }

    // Getters for thresholds
    public int getHungryThreshold() {
        return hungryThreshold;
    }

    public int getThirstyThreshold() {
        return thirstyThreshold;
    }

    public int getTiredThreshold() {
        return tiredThreshold;
    }

    public int getSadThreshold() {
        return sadThreshold;
    }
}
