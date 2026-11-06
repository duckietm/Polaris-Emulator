package polaris.threading.runnables;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

public class UserItemNewState implements Runnable {
    private final UserItem item;
    private final Room room;
    private final String state;

    public UserItemNewState(UserItem item, Room room, String state) {
        this.item = item;
        this.room = room;
        this.state = state;
    }

    @Override
    public void run() {
        this.item.setExtradata(this.state);

        if (this.item.getRoomId() == this.room.getId()) {
            this.room.updateItemState(this.item);
        }
    }
}
