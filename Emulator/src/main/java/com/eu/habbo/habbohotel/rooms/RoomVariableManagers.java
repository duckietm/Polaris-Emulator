package com.eu.habbo.habbohotel.rooms;

/** Owns the lifecycle of the room-scoped wired variable stores. */
final class RoomVariableManagers {
    private final RoomUserVariableManager user;
    private final RoomFurniVariableManager furni;
    private final RoomVariableManager room;
    private final RoomArrayVariableManager array;

    RoomVariableManagers(Room owner, RoomDependencies dependencies) {
        this.user = new RoomUserVariableManager(owner);
        this.furni = new RoomFurniVariableManager(owner);
        this.room = new RoomVariableManager(owner);
        this.array = new RoomArrayVariableManager(owner, dependencies);
    }

    RoomUserVariableManager user() {
        return this.user;
    }

    RoomFurniVariableManager furni() {
        return this.furni;
    }

    RoomVariableManager room() {
        return this.room;
    }

    RoomArrayVariableManager array() {
        return this.array;
    }
}
