package polaris.hotel.pets;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public abstract class PetAction {

    public final PetTasks petTask;
    public final boolean stopsPetWalking;
    public final List<RoomUnitStatus> statusToRemove = new ArrayList<>();
    public final List<RoomUnitStatus> statusToSet = new ArrayList<>();
    public int minimumActionDuration = 500;
    public String gestureToSet = null;

    protected PetAction(PetTasks petTask, boolean stopsPetWalking) {
        this.petTask = petTask;
        this.stopsPetWalking = stopsPetWalking;
    }

    public abstract boolean apply(Pet pet, User user, String[] data);
}
