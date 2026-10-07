package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore.Holder;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore.Order;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore.Write;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Users in the room are served live, absent users from their saved rows, and the two never race. */
class RoomUserVariableStoreTest {
    private static final int ROOM = 44;
    private static final int OTHER_ROOM = 45;
    private static final int POINTS = 10;
    private static final int TEMPORARY = 11;

    private final MemoryUserVariableRepository database = new MemoryUserVariableRepository();
    private final Map<Integer, Habbo> present = new ConcurrentHashMap<>();
    private final AtomicInteger clock = new AtomicInteger(1_000);
    private final ExecutorService threads = Executors.newFixedThreadPool(2);
    private RoomUserVariableManager manager;
    private RoomUserVariableStore store;

    @BeforeEach
    void setUp() throws Exception {
        WiredExtraUserVariable points = definition(POINTS, "points", WiredExtraUserVariable.AVAILABILITY_PERMANENT);
        WiredExtraUserVariable temporary = definition(TEMPORARY, "temp", WiredExtraUserVariable.AVAILABILITY_ROOM);
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(points, temporary));
        when(room.getId()).thenReturn(ROOM);
        when(room.getWiredTimezone()).thenReturn("UTC");
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(0, 0)).thenReturn(extras);
        when(special.getExtra(POINTS)).thenReturn(points);
        when(special.getExtra(TEMPORARY)).thenReturn(temporary);
        when(room.getArrayVariableManager()).thenReturn(mock(RoomArrayVariableManager.class));
        when(room.getHabbo(anyInt())).thenAnswer(call -> this.present.get(call.<Integer>getArgument(0)));
        when(room.getHabbo(anyString()))
                .thenAnswer(call -> this.present.values().stream()
                        .filter(habbo -> habbo.getHabboInfo().getUsername().equals(call.getArgument(0)))
                        .findFirst()
                        .orElse(null));
        this.manager = new RoomUserVariableManager(room, this.database, this.clock::get);
        this.store = this.manager.getStore();
    }

    @AfterEach
    void tearDown() {
        this.threads.shutdownNow();
    }

    @Test
    void absentUsersAreServedFromTheirSavedRows() {
        this.database.save(ROOM, 7, POINTS, 5, 100, 200);
        this.database.save(ROOM, 7, TEMPORARY, 1, 100, 200);
        this.database.save(OTHER_ROOM, 8, POINTS, 9, 100, 200);
        this.database.usernames.put(7, "dave");
        this.database.usernames.put(8, "erin");

        assertTrue(this.store.participates(7));
        assertFalse(this.store.participates(8), "a row in another room does not count");
        assertFalse(this.store.participates(9));
        assertEquals("dave", this.store.name(7));
        assertEquals(7, this.store.userIdByName("dave"));
        assertEquals(0, this.store.userIdByName("erin"));

        assertEquals(new RoomUserVariableStore.Value(5, 100, 200), this.store.get(7, POINTS));
        assertNull(this.store.get(7, TEMPORARY), "only saved variables are read from rows");
        assertEquals(Map.of(POINTS, new RoomUserVariableStore.Value(5, 100, 200)), this.store.all(7, List.of(POINTS)));

        assertEquals(Write.WRITTEN, this.store.put(7, POINTS, 12));
        assertEquals(new RoomUserVariableStore.Value(12, 100, 1_000), this.store.get(7, POINTS));
        this.clock.set(1_500);
        assertEquals(Write.WRITTEN, this.store.update(7, POINTS, 13));
        assertEquals(new RoomUserVariableStore.Value(13, 100, 1_500), this.store.get(7, POINTS));
        assertEquals(Write.NOT_SAVED, this.store.put(7, TEMPORARY, 3));
        assertEquals(1, this.database.row(ROOM, 7, TEMPORARY).value());

        assertEquals(Write.WRITTEN, this.store.remove(7, POINTS));
        assertNull(this.database.row(ROOM, 7, POINTS));
        assertEquals(Write.NOT_HELD, this.store.remove(7, POINTS));
        assertEquals(Write.NOT_HELD, this.store.update(7, POINTS, 1));

        assertEquals(9, this.database.row(OTHER_ROOM, 8, POINTS).value(), "other rooms are never touched");
        assertFalse(this.manager.hasVariable(7, POINTS), "nothing is left in the live store");
    }

    @Test
    void usersInTheRoomStayLiveAndKeepTheirValuesWhenTheyLeave() {
        this.database.save(ROOM, 7, POINTS, 5, 100, 200);
        this.enter(7, "dave");

        assertEquals(5, this.store.get(7, POINTS).value());
        this.database.save(ROOM, 7, POINTS, 99, 100, 200);
        assertEquals(5, this.store.get(7, POINTS).value(), "the live value wins over the row");

        assertEquals(Write.WRITTEN, this.store.put(7, POINTS, 8));
        assertEquals(8, this.manager.getCurrentValue(7, POINTS));
        assertEquals(8, this.database.row(ROOM, 7, POINTS).value(), "live writes are stored at once");

        this.leave(7);
        assertEquals(8, this.store.get(7, POINTS).value());
        assertTrue(this.store.participates(7));
    }

    @Test
    void holderPagesMergeLiveAndSavedRowsInEveryOrder() {
        Random random = new Random(7);
        for (int user = 1; user <= 40; user++) {
            this.database.usernames.put(user, "user" + user);
            if (user % 9 != 0) {
                this.database.save(
                        ROOM, user, POINTS, random.nextInt(7) - 3, 100 + random.nextInt(5), 200 + random.nextInt(5));
            }
        }
        this.database.save(ROOM, -14, POINTS, 1, 1, 1);
        for (int user = 2; user <= 40; user += 4) {
            this.enter(user, "user" + user);
        }
        // The live store wins: a changed value, and a user in the room whose live value is gone.
        this.store.put(6, POINTS, 50);
        this.database.save(ROOM, 6, POINTS, -50, 1, 1);
        this.manager.removeVariable(10, POINTS);
        this.database.save(ROOM, 10, POINTS, 4, 1, 1);
        this.store.put(9, POINTS, 2);

        List<Holder> expected = new ArrayList<>();
        for (int user = 1; user <= 40; user++) {
            if (this.present.containsKey(user)) {
                if (this.manager.hasVariable(user, POINTS)) {
                    expected.add(new Holder(
                            user,
                            "user" + user,
                            this.manager.getCurrentValue(user, POINTS),
                            this.manager.getCreatedAt(user, POINTS),
                            this.manager.getUpdatedAt(user, POINTS)));
                }
            } else if (this.database.row(ROOM, user, POINTS) != null) {
                RoomUserVariableRepository.StoredAssignment row = this.database.row(ROOM, user, POINTS);
                expected.add(new Holder(user, "user" + user, row.value(), row.createdAt(), row.updatedAt()));
            }
        }
        assertEquals(expected.size(), this.store.count(POINTS));
        assertFalse(expected.stream().anyMatch(holder -> holder.userId() == 10));

        for (Order order : Order.values()) {
            for (boolean descending : List.of(false, true)) {
                Comparator<Holder> comparator = RoomUserVariableStore.comparator(order, descending);
                List<Holder> sorted = new ArrayList<>(expected);
                sorted.sort(comparator);
                for (int size : List.of(1, 3, 7, 50)) {
                    List<Holder> paged = new ArrayList<>();
                    for (int offset = 0; offset <= expected.size(); offset += size) {
                        paged.addAll(this.store.page(POINTS, order, descending, offset, size));
                    }
                    assertEquals(sorted, paged, order + " " + (descending ? "desc" : "asc") + " size " + size);
                }
            }
        }
    }

    @Test
    void aSavedRowWriteFinishesBeforeTheUserEnteringMeanwhileIsLoaded() throws Exception {
        this.database.save(ROOM, 7, POINTS, 5, 100, 200);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        this.database.beforeUpsert = () -> {
            if (first.getAndSet(false)) {
                writing.countDown();
                await(release);
            }
        };

        Future<Write> write = this.threads.submit(() -> this.store.put(7, POINTS, 42));
        assertTrue(writing.await(5, TimeUnit.SECONDS));
        Future<?> entering = this.threads.submit(() -> this.enter(7, "dave"));
        Thread.sleep(200);
        assertFalse(entering.isDone(), "loading the user waits for the saved-row write");

        release.countDown();
        assertEquals(Write.WRITTEN, write.get(5, TimeUnit.SECONDS));
        entering.get(5, TimeUnit.SECONDS);
        assertEquals(42, this.manager.getCurrentValue(7, POINTS), "the load read the written row");
        assertEquals(42, this.database.row(ROOM, 7, POINTS).value());
    }

    @Test
    void aWriteDuringTheLoadOfAnEnteringUserWaitsAndGoesLive() throws Exception {
        this.database.save(ROOM, 7, POINTS, 5, 100, 200);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        this.database.afterFindByUserRead = () -> {
            if (first.getAndSet(false)) {
                loading.countDown();
                await(release);
            }
        };

        Future<?> entering = this.threads.submit(() -> this.enter(7, "dave"));
        assertTrue(loading.await(5, TimeUnit.SECONDS));
        Future<Write> write = this.threads.submit(() -> this.store.put(7, POINTS, 42));
        Thread.sleep(200);
        assertFalse(write.isDone(), "the write waits while the user's rows are being loaded");

        release.countDown();
        entering.get(5, TimeUnit.SECONDS);
        assertEquals(Write.WRITTEN, write.get(5, TimeUnit.SECONDS));
        assertEquals(42, this.manager.getCurrentValue(7, POINTS), "the old row did not overwrite the write");
        assertEquals(42, this.database.row(ROOM, 7, POINTS).value());
    }

    @Test
    void aUserLeavingDuringALiveWriteKeepsTheValue() {
        this.database.save(ROOM, 7, POINTS, 5, 100, 200);
        this.enter(7, "dave");
        AtomicBoolean first = new AtomicBoolean(true);
        this.database.beforeUpsert = () -> {
            if (first.getAndSet(false)) {
                this.leave(7);
            }
        };

        assertEquals(Write.WRITTEN, this.store.put(7, POINTS, 42));

        assertEquals(42, this.database.row(ROOM, 7, POINTS).value());
        assertFalse(this.manager.hasVariable(7, POINTS), "no live value is left behind for the absent user");
        this.enter(7, "dave");
        assertEquals(42, this.manager.getCurrentValue(7, POINTS));
    }

    @Test
    void aVariableClearedWhileAUserLoadsDoesNotComeBackFromTheOldRows() {
        this.database.save(ROOM, 7, POINTS, 5, 100, 200);
        this.database.save(ROOM, 8, POINTS, 6, 100, 200);
        AtomicBoolean first = new AtomicBoolean(true);
        this.database.afterFindByUserRead = () -> {
            if (first.getAndSet(false)) {
                this.manager.clearAllAssignments(POINTS);
            }
        };

        this.enter(7, "dave");

        assertFalse(this.manager.hasVariable(7, POINTS));
        assertNull(this.database.row(ROOM, 8, POINTS), "absent users lose the variable too");
    }

    @Test
    void bulkClearsCountAbsentUsers() {
        this.database.save(ROOM, 7, POINTS, 5, 100, 200);
        this.database.save(ROOM, 8, POINTS, 6, 100, 200);
        this.enter(7, "dave");

        assertEquals(1, this.store.countAbsent(POINTS));
        assertEquals(2, this.store.count(POINTS));
    }

    private void enter(int userId, String name) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(userId);
        when(info.getUsername()).thenReturn(name);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        this.present.put(userId, habbo);
        this.manager.restorePermanentAssignments(userId);
    }

    private void leave(int userId) {
        this.present.remove(userId);
        this.manager.clearAssignmentsForUser(userId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static WiredExtraUserVariable definition(int id, String name, int availability) throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn(
                        "{\"variableName\":\"" + name + "\",\"hasValue\":true,\"availability\":" + availability + "}");
        definition.loadWiredData(set, null);
        return definition;
    }
}
