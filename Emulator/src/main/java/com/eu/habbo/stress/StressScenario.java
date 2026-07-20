package com.eu.habbo.stress;

public record StressScenario(
        int roomId,
        int bots,
        int items,
        int rollers,
        int wiredStacks,
        int wiredEventsPerSecond,
        int itemId,
        int chatPerSecond,
        int durationSeconds,
        long seed,
        boolean movement) {

    public StressScenario validate(StressLimits limits) {
        if (this.roomId <= 0) {
            throw new IllegalArgumentException("room must be positive");
        }
        requireRange("bots", this.bots, limits.maxBots());
        requireRange("items", this.items, limits.maxItems());
        requireRange("rollers", this.rollers, limits.maxRollers());
        requireRange("wired stacks", this.wiredStacks, limits.maxWiredStacks());
        requireRange("wired event rate", this.wiredEventsPerSecond, limits.maxWiredEventsPerSecond());
        requireRange("chat rate", this.chatPerSecond, limits.maxChatPerSecond());
        requireRange("duration", this.durationSeconds, limits.maxDurationSeconds());
        long primaryEntities = (long) this.bots + this.items + this.rollers + this.wiredStacks;
        if (primaryEntities > limits.maxTotalEntities()) {
            throw new IllegalArgumentException(
                    "total primary entities must be between 0 and " + limits.maxTotalEntities());
        }
        if (this.itemId < 0) {
            throw new IllegalArgumentException("item id cannot be negative");
        }
        if (this.chatPerSecond > 0 && this.bots == 0) {
            throw new IllegalArgumentException("chat requires at least one bot");
        }
        if (this.wiredEventsPerSecond > 0 && this.wiredStacks == 0) {
            throw new IllegalArgumentException("wired events require at least one wired stack");
        }
        if (primaryEntities == 0) {
            throw new IllegalArgumentException("scenario must contain at least one workload");
        }
        return this;
    }

    private static void requireRange(String name, int value, int maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 0 and " + maximum);
        }
    }
}
