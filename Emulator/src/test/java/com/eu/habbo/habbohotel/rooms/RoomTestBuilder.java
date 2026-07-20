package com.eu.habbo.habbohotel.rooms;

import java.lang.reflect.Field;

final class RoomTestBuilder {

    private final Room room;

    private RoomTestBuilder(int id, int ownerId) {
        this.room = new Room(id, ownerId);
    }

    private RoomTestBuilder(int id, int ownerId, RoomDependencies dependencies) {
        this.room = new Room(id, ownerId, dependencies);
    }

    static RoomTestBuilder room(int id, int ownerId) {
        return new RoomTestBuilder(id, ownerId);
    }

    static RoomTestBuilder room(int id, int ownerId, RoomDependencies dependencies) {
        return new RoomTestBuilder(id, ownerId, dependencies);
    }

    RoomTestBuilder field(String name, Object value) {
        if (name.equals("promoted")) {
            this.room.getPromotionManager().setPromoted((boolean) value);
            return this;
        }
        if (name.equals("youtubeEnabled")) {
            this.room.setYoutubeEnabled((boolean) value);
            return this;
        }

        try {
            Field field = Room.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this.room, value);
            return this;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unknown Room field " + name, exception);
        }
    }

    Room build() {
        return this.room;
    }
}
