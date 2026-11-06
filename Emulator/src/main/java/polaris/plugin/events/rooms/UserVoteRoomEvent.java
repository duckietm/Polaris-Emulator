package polaris.plugin.events.rooms;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.plugin.Event;

public class UserVoteRoomEvent extends Event {
    public final Room room;
    public final User user;

    public UserVoteRoomEvent(Room room, User user) {
        this.room = room;
        this.user = user;
    }
}
