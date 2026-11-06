package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurnitureBuildheightEvent extends FurnitureUserEvent {

    public final double oldHeight;
    public final double newHeight;

    private double updatedHeight;

    private boolean changedHeight = false;

    public FurnitureBuildheightEvent(UserItem furniture, User user, double oldHeight, double newHeight) {
        super(furniture, user);

        this.oldHeight = oldHeight;
        this.newHeight = newHeight;
    }

    public void setNewHeight(double updatedHeight) {
        this.updatedHeight = updatedHeight;
        this.changedHeight = true;
    }

    public boolean hasChangedHeight() {
        return changedHeight;
    }

    public double getUpdatedHeight() {
        return updatedHeight;
    }
}
