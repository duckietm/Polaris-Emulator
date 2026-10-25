package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * A room's rights, bans and mutes were plain fastutil collections changed from packet threads, the
 * room cycle and loaders at once - a rehash under contention can loop forever - and giving rights
 * checked and added in two steps, so two threads could both insert a room_rights row.
 */
class RoomRightsConcurrencyTest {

    @Test
    void aUserIsAddedOnceWhateverTheThreads() throws Exception {
        IntList rights = IntLists.synchronize(new IntArrayList());
        RoomRightsSetView view = new RoomRightsSetView(rights);
        AtomicInteger added = new AtomicInteger();

        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int user = 0; user < 500; user++) {
                    if (view.add(user)) {
                        added.incrementAndGet();
                    }
                }
            }));
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertEquals(500, rights.size(), "each user is in the list once");
        assertEquals(500, added.get(), "and only one caller was told it added them, so one row is stored");
    }

    @Test
    void givingRightsTrustsTheAtomicAdd() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomRightsManager.java"));
        int give = source.indexOf("public void giveRights(int userId) {");
        String body = source.substring(give, source.indexOf("INSERT INTO room_rights", give));

        assertTrue(body.contains("if (this.rights.add(userId)) {"), body);
        assertFalse(body.contains("this.rights.contains(userId)"), "no separate check before the add");
    }

    @Test
    void theRoomHoldsSynchronizedCollections() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/Room.java"));

        assertEquals(
                2, count(source, "this.bannedHabbos = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());"));
        assertEquals(2, count(source, "this.mutedHabbos = Int2IntMaps.synchronize(new Int2IntOpenHashMap());"));
        assertEquals(2, count(source, "this.rights = IntLists.synchronize(new IntArrayList());"));
    }

    @Test
    void bansAreSwappedInOnceRead() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomBanLoader.java"));

        int read = source.indexOf("loaded.put(userId, new RoomBan(set));");
        int swap = source.indexOf("synchronized (bannedHabbos) {");
        assertTrue(read > 0 && swap > read, "the stored bans are read before the map is touched:\n" + source);
    }

    private static int count(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0; index = source.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }
}
