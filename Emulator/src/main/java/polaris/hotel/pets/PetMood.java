package polaris.hotel.pets;

/**
 * Represents the various mood states a pet can be in based on its stats.
 */
public enum PetMood {
    EXHAUSTED("exhausted", 0),
    STARVING("starving", 1),
    PARCHED("parched", 2),
    DEPRESSED("depressed", 3),
    NEUTRAL("neutral", 4),
    HAPPY("happy", 5),
    ECSTATIC("ecstatic", 6);

    private final String key;
    private final int priority;

    PetMood(String key, int priority) {
        this.key = key;
        this.priority = priority;
    }

    public String getKey() {
        return this.key;
    }

    /**
     * Gets the priority of this mood. Lower values indicate more urgent moods.
     * @return The priority value
     */
    public int getPriority() {
        return this.priority;
    }

    /**
     * Checks if this mood is a negative/urgent mood that needs addressing.
     * @return true if this is a negative mood
     */
    public boolean isNegative() {
        return this.priority <= 3;
    }

    /**
     * Checks if this mood is a positive mood.
     * @return true if this is a positive mood
     */
    public boolean isPositive() {
        return this.priority >= 5;
    }
}
