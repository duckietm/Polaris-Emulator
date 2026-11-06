package polaris.hotel.pets;

import polaris.Emulator;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnitStatus;

/**
 * Manages pet AI behavior using a state machine pattern.
 * Handles autonomous pet actions and state transitions.
 */
public class PetBehaviorManager {
    private final Pet pet;
    private PetBehaviorState currentState;
    private long stateEnteredAt;
    private long lastAutonomousAction;

    // Configurable delays
    private int autonomousActionDelay;
    private int idleWanderMinMs;
    private int idleWanderMaxMs;

    /**
     * Represents the various behavioral states a pet can be in.
     */
    public enum PetBehaviorState {
        IDLE, // Standing around
        WANDERING, // Random walking
        FOLLOWING, // Following owner/user
        EXECUTING_COMMAND, // Doing a commanded action
        EATING, // At food bowl
        DRINKING, // At water bowl
        PLAYING, // With toy
        RESTING, // In nest/laying down
        BREEDING, // In breeding box
        DEAD // Monsterplant only
    }

    public PetBehaviorManager(Pet pet) {
        this.pet = pet;
        this.currentState = PetBehaviorState.IDLE;
        this.stateEnteredAt = System.currentTimeMillis();
        this.lastAutonomousAction = 0;
        this.loadConfig();
    }

    /**
     * Loads configuration values from the emulator config.
     */
    private void loadConfig() {
        this.autonomousActionDelay = Emulator.getConfig().getInt("pet.behavior.autonomous_action_delay", 5000);
        this.idleWanderMinMs = Emulator.getConfig().getInt("pet.behavior.idle_wander_min_ms", 10000);
        this.idleWanderMaxMs = Emulator.getConfig().getInt("pet.behavior.idle_wander_max_ms", 30000);
    }

    /**
     * Transitions the pet to a new behavior state.
     * @param newState The new state to transition to
     */
    public void transition(PetBehaviorState newState) {
        if (this.currentState == newState) return;

        this.onExitState(this.currentState);
        this.currentState = newState;
        this.stateEnteredAt = System.currentTimeMillis();
        this.onEnterState(newState);
    }

    /**
     * Called when entering a new state to set up the appropriate room unit status.
     */
    private void onEnterState(PetBehaviorState state) {
        if (this.pet.getRoomUnit() == null) return;

        switch (state) {
            case RESTING -> {
                this.pet.getRoomUnit().setCanWalk(false);
                this.pet.getRoomUnit().setStatus(RoomUnitStatus.LAY, "0");
            }
            case EATING -> {
                this.pet.getRoomUnit().setStatus(RoomUnitStatus.EAT, "0");
            }
            case PLAYING -> {
                // Play status handled by specific toy interaction
            }
            case IDLE -> {
                // Clear any lingering action statuses
            }
            case FOLLOWING -> {
                this.pet.getRoomUnit().setCanWalk(true);
            }
            default -> {
                // No special handling needed
            }
        }
    }

    /**
     * Called when exiting a state to clean up room unit status.
     */
    private void onExitState(PetBehaviorState state) {
        if (this.pet.getRoomUnit() == null) return;

        switch (state) {
            case RESTING -> {
                this.pet.getRoomUnit().removeStatus(RoomUnitStatus.LAY);
                this.pet.getRoomUnit().setCanWalk(true);
            }
            case EATING -> {
                this.pet.getRoomUnit().removeStatus(RoomUnitStatus.EAT);
            }
            case PLAYING -> {
                // Play status cleanup handled by toy interaction
            }
            default -> {
                // No special cleanup needed
            }
        }
    }

    /**
     * Processes autonomous pet behavior each cycle.
     * Called every cycle to handle autonomous pet actions based on needs.
     */
    public void processAutonomousBehavior() {
        // Rate limit autonomous actions
        if (System.currentTimeMillis() - this.lastAutonomousAction < this.autonomousActionDelay) {
            return;
        }

        if (this.pet.getRoom() == null) return;

        PetStatsManager stats = this.pet.getStatsManager();
        if (stats == null) return;

        // Priority-based autonomous behavior
        if (stats.needsRest() && this.currentState != PetBehaviorState.RESTING) {
            this.pet.findNest();
            this.lastAutonomousAction = System.currentTimeMillis();
            return;
        }

        if (stats.needsFood() && this.currentState != PetBehaviorState.EATING) {
            this.pet.eat();
            this.lastAutonomousAction = System.currentTimeMillis();
            return;
        }

        if (stats.needsWater() && this.currentState != PetBehaviorState.DRINKING) {
            this.pet.drink();
            this.lastAutonomousAction = System.currentTimeMillis();
            return;
        }

        if (stats.needsAttention() && this.currentState == PetBehaviorState.IDLE) {
            this.pet.findToy();
            this.lastAutonomousAction = System.currentTimeMillis();
            return;
        }

        // Random wandering when idle
        if (this.currentState == PetBehaviorState.IDLE) {
            long idleTime = System.currentTimeMillis() - this.stateEnteredAt;
            int wanderDelay =
                    this.idleWanderMinMs + Emulator.getRandom().nextInt(this.idleWanderMaxMs - this.idleWanderMinMs);

            if (idleTime > wanderDelay) {
                RoomTile tile = this.pet.getRoom().getRandomWalkableTile();
                if (tile != null && this.pet.getRoomUnit() != null) {
                    this.pet.getRoomUnit().setGoalLocation(tile);
                    this.transition(PetBehaviorState.WANDERING);
                }
                this.lastAutonomousAction = System.currentTimeMillis();
            }
        }
    }

    /**
     * Checks if the pet can currently accept commands.
     * @return true if the pet can accept commands
     */
    public boolean canAcceptCommand() {
        return this.currentState != PetBehaviorState.DEAD && this.currentState != PetBehaviorState.BREEDING;
    }

    /**
     * Interrupts the current action and returns to idle state.
     */
    public void interruptCurrentAction() {
        if (this.currentState == PetBehaviorState.EXECUTING_COMMAND
                || this.currentState == PetBehaviorState.WANDERING) {
            this.transition(PetBehaviorState.IDLE);
        }
    }

    /**
     * Gets the current behavior state.
     * @return The current PetBehaviorState
     */
    public PetBehaviorState getCurrentState() {
        return this.currentState;
    }

    /**
     * Gets the timestamp when the current state was entered.
     * @return Timestamp in milliseconds
     */
    public long getStateEnteredAt() {
        return this.stateEnteredAt;
    }

    /**
     * Gets how long the pet has been in the current state.
     * @return Duration in milliseconds
     */
    public long getTimeInCurrentState() {
        return System.currentTimeMillis() - this.stateEnteredAt;
    }
}
