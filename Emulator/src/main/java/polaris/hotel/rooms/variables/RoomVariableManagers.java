package polaris.hotel.rooms.variables;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomDependencies;

/** Owns the lifecycle of the room-scoped wired variable stores. */
public final class RoomVariableManagers {
    private final RoomUserVariableManager user;
    private final RoomFurniVariableManager furni;
    private final RoomVariableManager room;
    private final RoomArrayVariableManager array;

    public RoomVariableManagers(Room owner, RoomDependencies dependencies) {
        this.user = new RoomUserVariableManager(owner);
        this.furni = new RoomFurniVariableManager(owner);
        this.room = new RoomVariableManager(owner);
        this.array = new RoomArrayVariableManager(owner, dependencies);
    }

    public RoomUserVariableManager user() {
        return this.user;
    }

    public RoomFurniVariableManager furni() {
        return this.furni;
    }

    public RoomVariableManager room() {
        return this.room;
    }

    public RoomArrayVariableManager array() {
        return this.array;
    }
}
