package polaris.plugin.events.support;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class SupportRoomActionEvent extends SupportEvent {

    public final Room room;

    public boolean kickUsers;

    public boolean lockDoor;

    public boolean changeTitle;

    public SupportRoomActionEvent(User moderator, Room room, boolean kickUsers, boolean lockDoor, boolean changeTitle) {
        super(moderator);

        this.room = room;
        this.kickUsers = kickUsers;
        this.lockDoor = lockDoor;
        this.changeTitle = changeTitle;
    }
}
