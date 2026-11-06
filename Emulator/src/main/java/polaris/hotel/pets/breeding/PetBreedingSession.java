package polaris.hotel.pets.breeding;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import polaris.Emulator;
import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.users.User;

/**
 * Represents an active breeding session between two pets.
 * Manages the state and lifecycle of the breeding process.
 */
public class PetBreedingSession {
    private final InteractionPetBreedingNest nest;
    private final Pet petOne;
    private Pet petTwo;
    private final long startTime;
    private BreedingState state;
    private ScheduledFuture<?> timeoutTask;

    /**
     * Represents the various states of a breeding session.
     */
    public enum BreedingState {
        WAITING_FOR_SECOND_PET,
        WAITING_FOR_CONFIRMATION,
        BREEDING_IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    /**
     * Creates a new breeding session with the first pet.
     * @param nest The breeding nest item
     * @param firstPet The first pet to enter the nest
     */
    public PetBreedingSession(InteractionPetBreedingNest nest, Pet firstPet) {
        this.nest = nest;
        this.petOne = firstPet;
        this.petTwo = null;
        this.startTime = System.currentTimeMillis();
        this.state = BreedingState.WAITING_FOR_SECOND_PET;

        // Auto-cancel if second pet doesn't arrive within configured timeout
        int timeoutSeconds = Emulator.getConfig().getInt("pet.breeding.timeout_seconds", 120);
        this.timeoutTask = Emulator.getThreading()
                .getService()
                .schedule(
                        () -> {
                            if (this.state == BreedingState.WAITING_FOR_SECOND_PET) {
                                this.cancel("Timeout waiting for second pet");
                            }
                        },
                        timeoutSeconds,
                        TimeUnit.SECONDS);
    }

    /**
     * Attempts to add the second pet to the breeding session.
     * @param pet The second pet to add
     * @return true if the pet was successfully added
     */
    public boolean addSecondPet(Pet pet) {
        if (this.state != BreedingState.WAITING_FOR_SECOND_PET) {
            return false;
        }

        // Validate compatibility - must be same pet type
        if (pet.getPetData().getType() != this.petOne.getPetData().getType()) {
            return false;
        }

        // Check if breeding is possible for this pet type
        if (pet.getPetData().getOffspringType() == -1) {
            return false;
        }

        // Don't allow breeding with self
        if (pet.getId() == this.petOne.getId()) {
            return false;
        }

        this.petTwo = pet;
        this.state = BreedingState.WAITING_FOR_CONFIRMATION;

        // Cancel the timeout task since we have both pets
        if (this.timeoutTask != null && !this.timeoutTask.isDone()) {
            this.timeoutTask.cancel(false);
        }

        return true;
    }

    /**
     * Confirms the breeding and starts the process.
     * @param user The user confirming the breeding
     * @param offspringName The name for the offspring
     */
    public void confirm(User user, String offspringName) {
        if (this.state != BreedingState.WAITING_FOR_CONFIRMATION) {
            return;
        }

        this.state = BreedingState.BREEDING_IN_PROGRESS;
        this.nest.breed(user, offspringName, this.petOne.getId(), this.petTwo.getId());
        this.state = BreedingState.COMPLETED;
    }

    /**
     * Cancels the breeding session and releases the pets.
     * @param reason The reason for cancellation
     */
    public void cancel(String reason) {
        if (this.state == BreedingState.COMPLETED || this.state == BreedingState.CANCELLED) {
            return;
        }

        this.state = BreedingState.CANCELLED;

        // Release first pet
        if (this.petOne != null && this.petOne.getRoomUnit() != null) {
            this.petOne.getRoomUnit().setCanWalk(true);
            this.petOne.setTask(PetTasks.FREE);
        }

        // Release second pet
        if (this.petTwo != null && this.petTwo.getRoomUnit() != null) {
            this.petTwo.getRoomUnit().setCanWalk(true);
            this.petTwo.setTask(PetTasks.FREE);
        }

        // Reset nest state
        this.nest.setExtradata("0");
        if (this.nest.getRoomId() > 0) {
            polaris.hotel.rooms.Room room =
                    polaris.Emulator.getGameEnvironment().getRoomManager().getRoom(this.nest.getRoomId());
            if (room != null) {
                room.updateItem(this.nest);
            }
        }

        // Cancel any pending timeout task
        if (this.timeoutTask != null && !this.timeoutTask.isDone()) {
            this.timeoutTask.cancel(false);
        }
    }

    /**
     * Checks if the breeding session is still valid.
     * @return true if both pets are still in the same room
     */
    public boolean isValid() {
        if (this.petOne == null || this.petOne.getRoom() == null) {
            return false;
        }

        if (this.petTwo != null && this.petTwo.getRoom() != this.petOne.getRoom()) {
            return false;
        }

        return true;
    }

    // Getters
    public InteractionPetBreedingNest getNest() {
        return nest;
    }

    public Pet getPetOne() {
        return petOne;
    }

    public Pet getPetTwo() {
        return petTwo;
    }

    public long getStartTime() {
        return startTime;
    }

    public BreedingState getState() {
        return state;
    }

    /**
     * Gets how long the session has been active in milliseconds.
     * @return Duration in milliseconds
     */
    public long getDuration() {
        return System.currentTimeMillis() - this.startTime;
    }
}
