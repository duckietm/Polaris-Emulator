package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredDailyTaskSupport;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A user's variables in one room whether or not the user is in it, for the variables web API. A user
 * the live store has loaded and who is in the room is served live, as before; anyone else by their
 * saved rows ({@code room_user_wired_variables}), which only permanent variables have.
 *
 * <p>Every call holds the user's lock ({@link UserVariableLocks}), which loading a user who enters
 * takes too: a saved-row write lands before that load reads, or waits until it is done and then goes
 * live. A user who leaves during a live write keeps it, as live writes of permanent variables are
 * stored at once; the write is repeated on the saved row in case it missed the live store. Pets and
 * bots are only served live: callers use this for users.
 */
public final class RoomUserVariableStore {
    public enum Order {
        ID,
        VALUE,
        CREATION_TIME,
        UPDATE_TIME
    }

    public enum Write {
        WRITTEN,
        /** Nothing to update or remove, or the write was refused. */
        NOT_HELD,
        /** The user is not in the room and the variable is not saved for users who leave. */
        NOT_SAVED
    }

    /** A stored value; {@code value} is null for a variable without one. Times are unix seconds. */
    public record Value(Integer value, int createdAt, int updatedAt) {}

    /** One holder in a page; {@code name} is null when unknown. */
    public record Holder(int userId, String name, Integer value, int createdAt, int updatedAt) {}

    private final Room room;
    private final RoomUserVariableManager live;
    private final RoomUserVariableRepository repository;
    private final IntSupplier currentTimestamp;

    RoomUserVariableStore(
            Room room,
            RoomUserVariableManager live,
            RoomUserVariableRepository repository,
            IntSupplier currentTimestamp) {
        this.room = room;
        this.live = live;
        this.repository = repository;
        this.currentTimestamp = currentTimestamp;
    }

    /**
     * Runs work while the user can not be loaded into the room (they can still leave); calls for the
     * same user may nest.
     */
    public <T> T withUser(int userId, Supplier<T> work) {
        if (userId <= 0) {
            return work.get();
        }
        ReentrantLock lock = UserVariableLocks.of(this.room.getId(), userId);
        lock.lock();
        try {
            return work.get();
        } finally {
            this.live.dropIfAbsent(userId);
            lock.unlock();
        }
    }

    /** Whether the user is in the room or has a saved row in it. */
    public boolean participates(int userId) {
        if (userId <= 0) {
            return false;
        }
        if (UserVariableHolders.isInRoom(this.room, userId)) {
            return true;
        }
        try {
            return this.repository.hasUser(this.room.getId(), userId);
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    /** The user's name: from the room while they are in it, else from the users table; null when unknown. */
    public String name(int userId) {
        String name = UserVariableHolders.nameOf(this.room, userId);
        if (!name.isEmpty()) {
            return name;
        }
        try {
            return userId > 0 ? this.repository.username(userId) : null;
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    /** The id of the user with this name in the room, or with saved rows in it; else 0. */
    public int userIdByName(String username) {
        Habbo habbo = this.room.getHabbo(username);
        if (habbo != null && habbo.getHabboInfo() != null) {
            return habbo.getHabboInfo().getId();
        }
        try {
            return this.repository.participantIdByName(this.room.getId(), username);
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public Value get(int userId, int definitionItemId) {
        return this.withUser(userId, () -> {
            if (this.live.isLive(userId)) {
                Value value = this.liveValue(userId, definitionItemId);
                if (value != null || this.live.isLive(userId)) {
                    return value;
                }
            }
            return this.savedValue(userId, definitionItemId);
        });
    }

    /** The user's values of these variables, by definition id. */
    public Map<Integer, Value> all(int userId, Collection<Integer> definitionItemIds) {
        return this.withUser(userId, () -> {
            Map<Integer, Value> values = new LinkedHashMap<>();
            if (this.live.isLive(userId)) {
                for (Integer definitionItemId : definitionItemIds) {
                    Value value = this.liveValue(userId, definitionItemId);
                    if (value != null) {
                        values.put(definitionItemId, value);
                    }
                }
                if (this.live.isLive(userId)) {
                    return values;
                }
                values.clear();
            }
            List<RoomUserVariableRepository.StoredAssignment> rows;
            try {
                rows = this.repository.findByUser(this.room.getId(), userId);
            } catch (SQLException e) {
                throw failure(e);
            }
            int now = this.currentTimestamp.getAsInt();
            for (RoomUserVariableRepository.StoredAssignment row : rows) {
                if (definitionItemIds.contains(row.definitionItemId()) && this.isSaved(row.definitionItemId())) {
                    values.put(row.definitionItemId(), this.savedValue(row, now));
                }
            }
            return values;
        });
    }

    /** Gives the user the variable, replacing any value; {@code value} is ignored for a variable without one. */
    public Write put(int userId, int definitionItemId, Integer value) {
        return this.withUser(userId, () -> {
            if (this.live.isLive(userId)) {
                boolean written = this.live.assignVariable(userId, definitionItemId, value, true);
                if (this.live.isLive(userId)) {
                    return written ? Write.WRITTEN : Write.NOT_HELD;
                }
            }
            WiredExtraUserVariable definition = this.savedDefinition(definitionItemId);
            if (definition == null) {
                return Write.NOT_SAVED;
            }
            int now = this.currentTimestamp.getAsInt();
            try {
                this.repository.upsert(
                        this.room.getId(), userId, definitionItemId, definition.hasValue() ? value : null, now, now);
            } catch (SQLException e) {
                throw failure(e);
            }
            return Write.WRITTEN;
        });
    }

    /** Changes the value of a variable the user has. */
    public Write update(int userId, int definitionItemId, int value) {
        return this.withUser(userId, () -> {
            if (this.live.isLive(userId)) {
                this.live.updateVariableValue(userId, definitionItemId, value);
                if (this.live.isLive(userId)) {
                    return this.live.hasVariable(userId, definitionItemId) ? Write.WRITTEN : Write.NOT_HELD;
                }
            }
            WiredExtraUserVariable definition = this.savedDefinition(definitionItemId);
            if (definition == null) {
                return Write.NOT_SAVED;
            }
            try {
                return this.repository.updateValue(
                                this.room.getId(),
                                userId,
                                definitionItemId,
                                definition.hasValue() ? value : null,
                                this.currentTimestamp.getAsInt())
                        ? Write.WRITTEN
                        : Write.NOT_HELD;
            } catch (SQLException e) {
                throw failure(e);
            }
        });
    }

    public Write remove(int userId, int definitionItemId) {
        return this.withUser(userId, () -> {
            if (this.live.isLive(userId)) {
                boolean removed = this.live.removeVariable(userId, definitionItemId);
                if (this.live.isLive(userId)) {
                    return removed ? Write.WRITTEN : Write.NOT_HELD;
                }
            }
            if (this.savedDefinition(definitionItemId) == null) {
                return Write.NOT_SAVED;
            }
            try {
                return this.repository.delete(this.room.getId(), userId, definitionItemId)
                        ? Write.WRITTEN
                        : Write.NOT_HELD;
            } catch (SQLException e) {
                throw failure(e);
            }
        });
    }

    /**
     * One page of the users holding a variable: the users in the room from the live store, merged with
     * the saved rows of everybody else, in one order. Only the rows the page can need are read.
     */
    public List<Holder> page(int definitionItemId, Order order, boolean descending, int offset, int limit) {
        WiredVariableDefinitionInfo info = this.live.getDefinitionInfo(definitionItemId);
        if (info == null || limit <= 0 || offset < 0) {
            return List.of();
        }
        Order effectiveOrder = !info.hasValue() && order == Order.VALUE ? Order.ID : order;
        Comparator<Holder> comparator = comparator(effectiveOrder, descending);

        Set<Integer> liveUsers = this.live.liveUsers();
        List<Holder> present = this.liveHolders(liveUsers, definitionItemId, info.hasValue());
        present.sort(comparator);

        int savedOffset = Math.max(0, offset - present.size());
        int wanted = (int) Math.min(Integer.MAX_VALUE, (long) offset + limit - savedOffset);
        List<Holder> saved = List.of();
        if (this.isSaved(definitionItemId)) {
            saved = this.savedHolders(
                    definitionItemId, effectiveOrder, descending, liveUsers, savedOffset, wanted, info.hasValue());
        }
        return merge(present, saved, savedOffset, saved.size() >= wanted, comparator, offset, limit);
    }

    /** How many users hold the variable, in the room or saved. */
    public int count(int definitionItemId) {
        WiredVariableDefinitionInfo info = this.live.getDefinitionInfo(definitionItemId);
        if (info == null) {
            return 0;
        }
        Set<Integer> liveUsers = this.live.liveUsers();
        return this.liveHolders(liveUsers, definitionItemId, info.hasValue()).size()
                + this.countSaved(definitionItemId, liveUsers);
    }

    /** How many users who are not in the room have a saved row of the variable. */
    public int countAbsent(int definitionItemId) {
        return this.countSaved(definitionItemId, this.live.liveUsers());
    }

    private int countSaved(int definitionItemId, Set<Integer> liveUsers) {
        if (!this.isSaved(definitionItemId)) {
            return 0;
        }
        try {
            return this.repository.countUsers(this.room.getId(), definitionItemId, liveUsers);
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    private List<Holder> liveHolders(Set<Integer> liveUsers, int definitionItemId, boolean hasValue) {
        List<Holder> holders = new ArrayList<>();
        for (Integer userId : liveUsers) {
            if (!this.live.hasVariable(userId, definitionItemId)) {
                continue;
            }
            holders.add(new Holder(
                    userId,
                    UserVariableHolders.nameOf(this.room, userId),
                    hasValue ? this.live.getCurrentValue(userId, definitionItemId) : null,
                    this.live.getCreatedAt(userId, definitionItemId),
                    this.live.getUpdatedAt(userId, definitionItemId)));
        }
        return holders;
    }

    private List<Holder> savedHolders(
            int definitionItemId,
            Order order,
            boolean descending,
            Set<Integer> liveUsers,
            int offset,
            int limit,
            boolean hasValue) {
        InteractionWiredExtra extra = this.live.definitionExtraOf(definitionItemId);
        int now = this.currentTimestamp.getAsInt();
        Integer dayStart = WiredDailyTaskSupport.isDailyCounter(this.room, extra)
                ? (int) WiredDailyTaskSupport.dayStart(this.room, now)
                : null;
        List<RoomUserVariableRepository.SavedUser> rows;
        try {
            rows = this.repository.pageUsers(
                    this.room.getId(), definitionItemId, order, descending, dayStart, liveUsers, offset, limit);
        } catch (SQLException e) {
            throw failure(e);
        }
        List<Holder> holders = new ArrayList<>(rows.size());
        for (RoomUserVariableRepository.SavedUser row : rows) {
            Integer value = hasValue
                    ? WiredDailyTaskSupport.effectiveValue(this.room, extra, row.value(), row.updatedAt(), now)
                    : null;
            holders.add(new Holder(row.userId(), row.username(), value, row.createdAt(), row.updatedAt()));
        }
        return holders;
    }

    private Value liveValue(int userId, int definitionItemId) {
        if (!this.live.hasVariable(userId, definitionItemId)) {
            return null;
        }
        WiredVariableDefinitionInfo info = this.live.getDefinitionInfo(definitionItemId);
        return new Value(
                info != null && info.hasValue() ? this.live.getCurrentValue(userId, definitionItemId) : null,
                this.live.getCreatedAt(userId, definitionItemId),
                this.live.getUpdatedAt(userId, definitionItemId));
    }

    private Value savedValue(int userId, int definitionItemId) {
        if (!this.isSaved(definitionItemId)) {
            return null;
        }
        RoomUserVariableRepository.StoredAssignment row;
        try {
            row = this.repository.find(this.room.getId(), userId, definitionItemId);
        } catch (SQLException e) {
            throw failure(e);
        }
        return row == null ? null : this.savedValue(row, this.currentTimestamp.getAsInt());
    }

    private Value savedValue(RoomUserVariableRepository.StoredAssignment row, int now) {
        WiredExtraUserVariable definition = this.live.definitionOf(row.definitionItemId());
        Integer value = definition != null && definition.hasValue()
                ? WiredDailyTaskSupport.effectiveValue(this.room, definition, row.value(), row.updatedAt(), now)
                : null;
        return new Value(value, row.createdAt(), row.updatedAt());
    }

    /** Whether users who leave keep the variable: a permanent plain custom variable. */
    boolean isSaved(int definitionItemId) {
        return this.savedDefinition(definitionItemId) != null;
    }

    private WiredExtraUserVariable savedDefinition(int definitionItemId) {
        WiredExtraUserVariable definition = this.live.definitionOf(definitionItemId);
        return definition != null && definition.isPermanentAvailability() && !definition.isArray() ? definition : null;
    }

    /** The web API's order: the key, then the user id; descending reverses both. Missing values first. */
    static Comparator<Holder> comparator(Order order, boolean descending) {
        Comparator<Holder> byId = Comparator.comparingInt(Holder::userId);
        Comparator<Holder> comparator =
                switch (order) {
                    case ID -> byId;
                    case VALUE ->
                        Comparator.comparing(Holder::value, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()))
                                .thenComparing(byId);
                    case CREATION_TIME ->
                        Comparator.comparingInt(Holder::createdAt).thenComparing(byId);
                    case UPDATE_TIME ->
                        Comparator.comparingInt(Holder::updatedAt).thenComparing(byId);
                };
        return descending ? comparator.reversed() : comparator;
    }

    /**
     * The holders at {@code [offset, offset + limit)} of the merged order. {@code saved} holds the saved
     * rows from rank {@code savedOffset} on, in the same order; {@code savedFull} says whether more may
     * follow it. A live holder sorting before the first of them (when rows were skipped) is on an earlier
     * page, one sorting after the last of a full read on a later one.
     */
    static List<Holder> merge(
            List<Holder> live,
            List<Holder> saved,
            int savedOffset,
            boolean savedFull,
            Comparator<Holder> comparator,
            int offset,
            int limit) {
        List<Holder> page = new ArrayList<>();
        int savedIndex = 0;
        int liveIndex = 0;
        while (savedIndex < saved.size() || liveIndex < live.size()) {
            boolean takeSaved = liveIndex >= live.size()
                    || (savedIndex < saved.size()
                            && comparator.compare(saved.get(savedIndex), live.get(liveIndex)) < 0);
            Holder holder;
            long position;
            if (takeSaved) {
                holder = saved.get(savedIndex);
                position = (long) savedOffset + savedIndex + liveIndex;
                savedIndex++;
            } else {
                holder = live.get(liveIndex);
                boolean earlier = savedIndex == 0 && savedOffset > 0;
                boolean later = savedIndex == saved.size() && savedFull;
                position = earlier || later ? -1 : (long) savedOffset + savedIndex + liveIndex;
                liveIndex++;
            }
            if (position >= offset && position < (long) offset + limit) {
                page.add(holder);
            }
        }
        return page;
    }

    private static IllegalStateException failure(SQLException e) {
        return new IllegalStateException("Saved wired user variables could not be read or written", e);
    }
}
