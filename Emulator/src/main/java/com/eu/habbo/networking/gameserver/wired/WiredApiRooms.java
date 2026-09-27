package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import java.util.List;

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

    enum TargetKind {
        USERS("users", Scope.USER),
        PETS("pets", Scope.USER),
        BOTS("bots", Scope.USER),
        FLOOR("floor", Scope.FURNI),
        WALL("wall", Scope.FURNI);

        private final String path;
        private final Scope scope;

        TargetKind(String path, Scope scope) {
            this.path = path;
            this.scope = scope;
        }

        String path() {
            return this.path;
        }

        Scope scope() {
            return this.scope;
        }

        static TargetKind fromPath(String value) {
            for (TargetKind kind : values()) {
                if (kind.path.equals(value)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** A permanent custom variable the API exposes. */
    record Variable(String name, Scope scope, int definitionItemId, boolean hasValue, boolean textConnected) {}

    /** One holder's value; {@code value} is null for a variable without a value. Times are unix seconds. */
    record Entry(int entityId, Integer value, long createdAt, long updatedAt) {}

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

        boolean holderExists(TargetKind kind, int entityId);

        /** The display name of a holder, or null. */
        String holderName(TargetKind kind, int entityId);

        /** The id of the user with this name in the room, or 0. */
        int userIdByName(String username);

        Entry entry(Variable variable, TargetKind kind, int entityId);

        List<Entry> holders(Variable variable, TargetKind kind);

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
