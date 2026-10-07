package com.eu.habbo.habbohotel.rooms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code room_user_wired_variables} and the user names in memory, answering like the SQL does
 * (missing values first when ascending, last when descending; the user id breaks ties). Hooks run
 * inside a call, to let a test move users in or out of the room at that moment.
 */
class MemoryUserVariableRepository extends RoomUserVariableRepository {
    record Key(int roomId, int userId, int definitionItemId) {}

    final Map<Key, StoredAssignment> rows = new ConcurrentHashMap<>();
    final Map<Integer, String> usernames = new HashMap<>();
    volatile Runnable afterFindByUserRead = () -> {};
    volatile Runnable beforeUpsert = () -> {};

    MemoryUserVariableRepository() {
        super(() -> null);
    }

    void save(int roomId, int userId, int definitionItemId, Integer value, int createdAt, int updatedAt) {
        this.rows.put(
                new Key(roomId, userId, definitionItemId),
                new StoredAssignment(definitionItemId, value, createdAt, updatedAt));
    }

    StoredAssignment row(int roomId, int userId, int definitionItemId) {
        return this.rows.get(new Key(roomId, userId, definitionItemId));
    }

    @Override
    List<StoredAssignment> findByUser(int roomId, int userId) {
        List<StoredAssignment> found = new ArrayList<>();
        this.rows.forEach((key, row) -> {
            if (key.roomId() == roomId && key.userId() == userId) {
                found.add(row);
            }
        });
        this.afterFindByUserRead.run();
        return found;
    }

    @Override
    Map<Integer, List<StoredAssignment>> findUnitHolders(int roomId) {
        Map<Integer, List<StoredAssignment>> found = new HashMap<>();
        this.rows.forEach((key, row) -> {
            if (key.roomId() == roomId && key.userId() < 0) {
                found.computeIfAbsent(key.userId(), ignored -> new ArrayList<>())
                        .add(row);
            }
        });
        return found;
    }

    @Override
    void upsert(int roomId, int userId, int definitionItemId, Integer value, int createdAt, int updatedAt) {
        this.beforeUpsert.run();
        this.rows.compute(
                new Key(roomId, userId, definitionItemId),
                (key, old) -> new StoredAssignment(
                        definitionItemId, value, old == null ? createdAt : old.createdAt(), updatedAt));
    }

    @Override
    boolean delete(int roomId, int userId, int definitionItemId) {
        return this.rows.remove(new Key(roomId, userId, definitionItemId)) != null;
    }

    @Override
    void deleteDefinition(int roomId, int definitionItemId) {
        this.rows.keySet().removeIf(key -> key.roomId() == roomId && key.definitionItemId() == definitionItemId);
    }

    @Override
    boolean hasDefinition(int roomId, int definitionItemId) {
        return this.rows.keySet().stream()
                .anyMatch(key -> key.roomId() == roomId && key.definitionItemId() == definitionItemId);
    }

    @Override
    StoredAssignment find(int roomId, int userId, int definitionItemId) {
        return this.row(roomId, userId, definitionItemId);
    }

    @Override
    boolean hasUser(int roomId, int userId) {
        return this.rows.keySet().stream().anyMatch(key -> key.roomId() == roomId && key.userId() == userId);
    }

    @Override
    boolean updateValue(int roomId, int userId, int definitionItemId, Integer value, int updatedAt) {
        return this.rows.computeIfPresent(
                        new Key(roomId, userId, definitionItemId),
                        (key, old) -> new StoredAssignment(definitionItemId, value, old.createdAt(), updatedAt))
                != null;
    }

    @Override
    List<SavedUser> pageUsers(
            int roomId,
            int definitionItemId,
            RoomUserVariableStore.Order order,
            boolean descending,
            Integer dayStart,
            Collection<Integer> excluded,
            int offset,
            int limit) {
        List<SavedUser> matching = this.users(roomId, definitionItemId, excluded);
        Comparator<SavedUser> byKey = Comparator.comparing(
                (SavedUser row) -> sortKey(row, order, dayStart), Comparator.nullsFirst(Comparator.naturalOrder()));
        Comparator<SavedUser> comparator = byKey.thenComparingInt(SavedUser::userId);
        matching.sort(descending ? comparator.reversed() : comparator);
        int from = Math.min(offset, matching.size());
        int to = (int) Math.min((long) from + limit, matching.size());
        return new ArrayList<>(matching.subList(from, to));
    }

    @Override
    int countUsers(int roomId, int definitionItemId, Collection<Integer> excluded) {
        return this.users(roomId, definitionItemId, excluded).size();
    }

    @Override
    String username(int userId) {
        return this.usernames.get(userId);
    }

    @Override
    int participantIdByName(int roomId, String username) {
        for (Map.Entry<Integer, String> user : this.usernames.entrySet()) {
            if (Objects.equals(user.getValue(), username) && this.hasUser(roomId, user.getKey())) {
                return user.getKey();
            }
        }
        return 0;
    }

    private List<SavedUser> users(int roomId, int definitionItemId, Collection<Integer> excluded) {
        List<SavedUser> matching = new ArrayList<>();
        this.rows.forEach((key, row) -> {
            if (key.roomId() == roomId
                    && key.definitionItemId() == definitionItemId
                    && key.userId() > 0
                    && !excluded.contains(key.userId())) {
                matching.add(new SavedUser(
                        key.userId(), this.usernames.get(key.userId()), row.value(), row.createdAt(), row.updatedAt()));
            }
        });
        return matching;
    }

    private static Integer sortKey(SavedUser row, RoomUserVariableStore.Order order, Integer dayStart) {
        return switch (order) {
            case ID -> 0;
            case VALUE ->
                dayStart != null && row.value() != null && row.value() != 0 && row.updatedAt() < dayStart
                        ? Integer.valueOf(0)
                        : row.value();
            case CREATION_TIME -> row.createdAt();
            case UPDATE_TIME -> row.updatedAt();
        };
    }
}
