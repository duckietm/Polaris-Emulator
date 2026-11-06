package polaris.threading.runnables;

import java.util.HashSet;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import polaris.plugin.EventHandler;
import polaris.plugin.events.users.UserTakeStepEvent;

public class RoomTrashing implements Runnable {
    public static RoomTrashing INSTANCE;

    private User user;
    private Room room;

    public RoomTrashing(User user, Room room) {
        this.user = user;
        this.room = room;

        RoomTrashing.INSTANCE = this;
    }

    @EventHandler
    public static void onUserWalkEvent(UserTakeStepEvent event) {
        if (INSTANCE == null) return;

        if (INSTANCE.user == null) return;

        if (!INSTANCE.user.isOnline()) INSTANCE.user = null;

        if (INSTANCE.user == event.user) {
            if (event.user.getUserInfo().getCurrentRoom() != null) {
                if (event.user.getUserInfo().getCurrentRoom().equals(INSTANCE.room)) {
                    Set<ServerMessage> messages = new HashSet<>();

                    Set<UserItem> items = INSTANCE.room.getItemsAt(event.toLocation);

                    int offset = Emulator.getRandom().nextInt(4) + 2;

                    RoomTile t = null;
                    while (offset > 0) {
                        t = INSTANCE.room
                                .getLayout()
                                .getTileInFront(
                                        INSTANCE.room.getLayout().getTile(event.toLocation.x, event.toLocation.y),
                                        event.user
                                                .getRoomUnit()
                                                .getBodyRotation()
                                                .getValue(),
                                        (short) offset);

                        if (!INSTANCE.room.getLayout().tileWalkable(t.x, t.y)) {
                            offset--;
                        } else {
                            break;
                        }
                    }

                    for (UserItem item : items) {
                        double offsetZ = (INSTANCE.room.getTopHeightAt(t.x, t.y)) - item.getZ();

                        messages.add(new FloorItemOnRollerComposer(item, null, t, offsetZ, INSTANCE.room).compose());
                    }

                    offset = Emulator.getRandom().nextInt(4) + 2;

                    t = null;
                    while (offset > 0) {
                        t = INSTANCE.room
                                .getLayout()
                                .getTileInFront(
                                        INSTANCE.room.getLayout().getTile(event.toLocation.x, event.toLocation.y),
                                        event.user
                                                        .getRoomUnit()
                                                        .getBodyRotation()
                                                        .getValue()
                                                + 7,
                                        (short) offset);

                        if (!INSTANCE.room.getLayout().tileWalkable(t.x, t.y)) {
                            offset--;
                        } else {
                            break;
                        }
                    }

                    RoomTile s = INSTANCE.room
                            .getLayout()
                            .getTileInFront(
                                    INSTANCE.user.getRoomUnit().getCurrentLocation(),
                                    INSTANCE.user
                                                    .getRoomUnit()
                                                    .getBodyRotation()
                                                    .getValue()
                                            + 7);

                    if (s != null) {
                        items = INSTANCE.room.getItemsAt(s);
                    }

                    for (UserItem item : items) {
                        double offsetZ = (INSTANCE.room.getTopHeightAt(t.x, t.y)) - item.getZ();

                        messages.add(new FloorItemOnRollerComposer(item, null, t, offsetZ, INSTANCE.room).compose());
                    }

                    offset = Emulator.getRandom().nextInt(4) + 2;

                    t = null;
                    while (offset > 0) {
                        t = INSTANCE.getRoom()
                                .getLayout()
                                .getTileInFront(
                                        event.toLocation,
                                        event.user
                                                        .getRoomUnit()
                                                        .getBodyRotation()
                                                        .getValue()
                                                + 1,
                                        (short) offset);

                        if (!INSTANCE.room.getLayout().tileWalkable(t.x, t.y)) {
                            offset--;
                        } else {
                            break;
                        }
                    }

                    s = INSTANCE.getRoom()
                            .getLayout()
                            .getTileInFront(
                                    INSTANCE.user.getRoomUnit().getCurrentLocation(),
                                    INSTANCE.user
                                                    .getRoomUnit()
                                                    .getBodyRotation()
                                                    .getValue()
                                            + 1);
                    items = INSTANCE.room.getItemsAt(s);

                    for (UserItem item : items) {
                        double offsetZ = (INSTANCE.room.getTopHeightAt(t.x, t.y)) - item.getZ();

                        messages.add(new FloorItemOnRollerComposer(item, null, t, offsetZ, INSTANCE.room).compose());
                    }

                    for (ServerMessage message : messages) {
                        INSTANCE.room.sendComposer(message);
                    }
                } else {
                    INSTANCE.user = null;
                    INSTANCE.room = null;
                }
            }
        }
    }

    @Override
    public void run() {}

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Room getRoom() {
        return this.room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }
}
