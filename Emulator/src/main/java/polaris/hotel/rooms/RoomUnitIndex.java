package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.concurrent.ConcurrentHashMap;
import polaris.hotel.bots.Bot;
import polaris.hotel.pets.Pet;
import polaris.hotel.users.User;

final class RoomUnitIndex {
    private final ConcurrentHashMap<Integer, User> users = new ConcurrentHashMap<>(3);
    private final Int2ObjectMap<User> queue = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2ObjectMap<Bot> bots = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2ObjectMap<Pet> pets = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private volatile int unitCounter;

    ConcurrentHashMap<Integer, User> users() {
        return users;
    }

    Int2ObjectMap<User> queue() {
        return queue;
    }

    Int2ObjectMap<Bot> bots() {
        return bots;
    }

    Int2ObjectMap<Pet> pets() {
        return pets;
    }

    int unitCounter() {
        return unitCounter;
    }

    int nextUnitId() {
        return unitCounter++;
    }

    void incrementUnitId() {
        unitCounter++;
    }

    void resetUnitCounter() {
        unitCounter = 0;
    }
}
