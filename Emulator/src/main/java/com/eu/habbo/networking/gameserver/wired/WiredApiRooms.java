package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore.Order;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The rooms the variables web API works on. The real implementation is {@link HotelWiredApiRooms};
 * tests supply their own.
 */
interface WiredApiRooms {

    /** The room if its data is loaded, else null. Never loads anything. */
    VariableRoom loadedRoom(int roomId);

    /**
     * The keys of the web-api box stored for a room that is not loaded, read without loading it.
     * Empty when the room has no such box.
     */
    List<WiredExtraVariableWebApi.KeyState> storedKeys(int roomId);

    /** Loads the room the normal way; null when it does not exist. */
    VariableRoom loadRoom(int roomId);

    enum Scope {
        USER("user"),
        FURNI("furni"),
        GLOBAL("global");

        private final String path;

        Scope(String path) {
            this.path = path;
        }

        String path() {
            return this.path;
        }

        static Scope fromPath(String value) {
            for (Scope scope : values()) {
                if (scope.path.equals(value)) {
                    return scope;
                }
            }
            return null;
        }
    }

    /**
     * Habbo's target kinds. Builders Club items have their own kinds, like on Habbo; their id is the
     * item id.
     */
    enum TargetKind {
        USERS("users", Scope.USER, "user", null),
        PETS("pets", Scope.USER, "pet", null),
        BOTS("bots", Scope.USER, "bot", null),
        FURNI("furni", Scope.FURNI, "furni", "floor"),
        FURNI_BC("furni-bc", Scope.FURNI, "furni_bc", null),
        WALL_ITEMS("wall-items", Scope.FURNI, "wall_item", "wall"),
        WALL_ITEMS_BC("wall-items-bc", Scope.FURNI, "wall_item_bc", null);

        private final String path;
        private final Scope scope;
        private final String profileKey;
        private final String alias;

        TargetKind(String path, Scope scope, String profileKey, String alias) {
            this.path = path;
            this.scope = scope;
            this.profileKey = profileKey;
            this.alias = alias;
        }

        String path() {
            return this.path;
        }

        Scope scope() {
            return this.scope;
        }

        /** The field a profile of this kind names its owner under. */
        String profileKey() {
            return this.profileKey;
        }

        boolean wall() {
            return this == WALL_ITEMS || this == WALL_ITEMS_BC;
        }

        boolean buildersClub() {
            return this == FURNI_BC || this == WALL_ITEMS_BC;
        }

        /** Habbo's kind name, or the older Polaris name ({@code floor}, {@code wall}). */
        static TargetKind fromPath(String value) {
            for (TargetKind kind : values()) {
                if (kind.path.equals(value) || (kind.alias != null && kind.alias.equals(value))) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** A permanent custom variable the API exposes. */
    record Variable(String name, Scope scope, int definitionItemId, boolean hasValue, boolean textConnected) {}

    /**
     * One holder's value; {@code value} is null for a variable without a value. Times are unix seconds.
     * {@code name} is the holder's name when the source knew it (a page of holders), else null.
     */
    record Entry(int entityId, Integer value, long createdAt, long updatedAt, String name) {
        Entry(int entityId, Integer value, long createdAt, long updatedAt) {
            this(entityId, value, createdAt, updatedAt, null);
        }
    }

    /** The web API's holder order: the key, then the holder id; descending reverses both. */
    static Comparator<Entry> order(Order order, boolean descending) {
        Comparator<Entry> byId = Comparator.comparingInt(Entry::entityId);
        Comparator<Entry> comparator =
                switch (order) {
                    case ID -> byId;
                    case VALUE ->
                        Comparator.comparing(Entry::value, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()))
                                .thenComparing(byId);
                    case CREATION_TIME ->
                        Comparator.comparingLong(Entry::createdAt).thenComparing(byId);
                    case UPDATE_TIME ->
                        Comparator.comparingLong(Entry::updatedAt).thenComparing(byId);
                };
        return descending ? comparator.reversed() : comparator;
    }

    /** Sorts holders that are all in memory and cuts out one page. */
    static List<Entry> page(List<Entry> entries, Order order, boolean descending, int offset, int limit) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(order(order, descending));
        int from = Math.min(Math.max(offset, 0), sorted.size());
        int to = (int) Math.min((long) from + limit, sorted.size());
        return List.copyOf(sorted.subList(from, to));
    }

    /** One loaded room with its web-api box and its permanent variables. */
    interface VariableRoom {
        int id();

        /** What a key hash opens on the room's web-api box, or null. */
        WiredExtraVariableWebApi.Access authenticate(byte[] keyHash);

        /** Whether the box stands in a room owned by the box owner. */
        boolean boxUsable();

        boolean bulkDeleteAllowed();

        /** The permanent custom variables, in a stable order. */
        List<Variable> variables();

        /** A user in the room or with saved values in it; a pet, bot or item in the room. */
        boolean holderExists(TargetKind kind, int entityId);

        /** The display name of a holder, or null. */
        String holderName(TargetKind kind, int entityId);

        /** The id of the user with this name in the room or with saved values in it, or 0. */
        int userIdByName(String username);

        Entry entry(Variable variable, TargetKind kind, int entityId);

        /** The holder's values of the variables of its kind's scope, in {@link #variables()} order. */
        default Map<Variable, Entry> entries(TargetKind kind, int entityId) {
            Map<Variable, Entry> entries = new LinkedHashMap<>();
            for (Variable variable : this.variables()) {
                if (variable.scope() == kind.scope()) {
                    Entry entry = this.entry(variable, kind, entityId);
                    if (entry != null) {
                        entries.put(variable, entry);
                    }
                }
            }
            return entries;
        }

        /** One page of the holders of a variable, sorted like {@link WiredApiRooms#order}. */
        List<Entry> holderPage(
                Variable variable, TargetKind kind, Order order, boolean descending, int offset, int limit);

        int holderCount(Variable variable, TargetKind kind);

        /**
         * Runs several calls for one holder as one step: a user can not come into the room in between
         * (where their values would move from the saved rows to the live store).
         */
        default <T> T atomically(TargetKind kind, int entityId, Supplier<T> work) {
            return work.get();
        }

        Entry global(Variable variable);

        /** Gives the holder the variable, replacing any value. {@code value} is null for value-less variables. */
        boolean assign(Variable variable, TargetKind kind, int entityId, Integer value);

        boolean update(Variable variable, TargetKind kind, int entityId, int value);

        boolean remove(Variable variable, TargetKind kind, int entityId);

        boolean updateGlobal(Variable variable, int value);

        /** Takes the variable from every holder; for a global variable, clears its value. */
        int clearAll(Variable variable);
    }
}
