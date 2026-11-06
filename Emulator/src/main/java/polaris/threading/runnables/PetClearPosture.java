package polaris.threading.runnables;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.rooms.RoomUnitStatus;

public class PetClearPosture implements Runnable {
    private final Pet pet;
    private final RoomUnitStatus key;
    private final PetTasks newTask;
    private final boolean clearTask;

    public PetClearPosture(Pet pet, RoomUnitStatus key, PetTasks newTask, boolean clearTask) {
        this.pet = pet;
        this.key = key;
        this.newTask = newTask;
        this.clearTask = clearTask;
    }

    @Override
    public void run() {
        if (this.pet != null) {
            if (this.pet.getRoom() != null) {
                if (this.pet.getRoomUnit() != null) {
                    this.pet.getRoomUnit().removeStatus(this.key);

                    if (this.clearTask) this.pet.setTask(PetTasks.FREE);
                    else if (this.newTask != null) this.pet.setTask(this.newTask);
                }
            }
        }
    }
}
