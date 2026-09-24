package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory rooms for the router tests. */
final class FakeWiredApiRooms implements WiredApiRooms {
    final Map<Integer, FakeRoom> loaded = new HashMap<>();
    final Map<Integer, FakeRoom> unloaded = new HashMap<>();
    int loads;
    int storedLookups;

    FakeRoom room(int id, String readKey, String writeKey) {
        FakeRoom room = new FakeRoom(id, readKey, writeKey);
        this.loaded.put(id, room);
        return room;
    }

    @Override
    public VariableRoom loadedRoom(int roomId) {
        return this.loaded.get(roomId);
    }

    @Override
    public List<WiredExtraVariableWebApi.KeyState> storedKeys(int roomId) {
        this.storedLookups++;
        FakeRoom room = this.unloaded.get(roomId);
        return room == null ? List.of() : List.of(room.keys);
    }

    @Override
    public VariableRoom loadRoom(int roomId) {
        this.loads++;
        FakeRoom room = this.unloaded.remove(roomId);
        if (room != null) {
            this.loaded.put(roomId, room);
        }
        return room;
    }

    static final class FakeRoom implements VariableRoom {
        final int id;
        WiredExtraVariableWebApi.KeyState keys;
        boolean usable = true;
        boolean bulk;
        final List<Variable> variables = new ArrayList<>();
        final Map<Integer, String> users = new LinkedHashMap<>();
        final Map<Integer, String> pets = new LinkedHashMap<>();
        final Map<Integer, String> bots = new LinkedHashMap<>();
        final Map<Integer, Boolean> furni = new LinkedHashMap<>();
        final Map<String, Map<Integer, Entry>> values = new HashMap<>();
        final Map<String, Integer> globals = new HashMap<>();
        int writes;
        long now = 1_000;

        FakeRoom(int id, String readKey, String writeKey) {
            this.id = id;
            this.keys = WiredExtraVariableWebApi.parseStored(
                    "{\"itemId\":1,\"readKey\":\"" + readKey + "\",\"writeKey\":\"" + writeKey
                            + "\",\"bulkDelete\":false}",
                    1);
        }

        Variable variable(String name, Scope scope, boolean hasValue) {
            Variable variable = new Variable(name, scope, 100 + this.variables.size(), hasValue, false);
            this.variables.add(variable);
            if (scope == Scope.GLOBAL) {
                this.globals.put(name, 0);
            }
            return variable;
        }

        FakeRoom user(int id, String name) {
            this.users.put(id, name);
            return this;
        }

        FakeRoom pet(int id, String name) {
            this.pets.put(id, name);
            return this;
        }

        FakeRoom bot(int id, String name) {
            this.bots.put(id, name);
            return this;
        }

        /** Values are stored under the server's holder key, so a pet and a user with the same id differ. */
        static int storeKey(TargetKind kind, int entityId) {
            return switch (kind) {
                case USERS -> UserVariableHolders.ofUser(entityId);
                case PETS -> UserVariableHolders.ofPet(entityId);
                case BOTS -> UserVariableHolders.ofBot(entityId);
                default -> entityId;
            };
        }

        void holdAs(TargetKind kind, String name, int entityId, Integer value) {
            this.values
                    .computeIfAbsent(name, key -> new LinkedHashMap<>())
                    .put(storeKey(kind, entityId), new Entry(entityId, value, this.now, this.now));
        }

        FakeRoom floor(int id) {
            this.furni.put(id, true);
            return this;
        }

        FakeRoom wall(int id) {
            this.furni.put(id, false);
            return this;
        }

        void hold(String name, int entityId, Integer value) {
            this.values
                    .computeIfAbsent(name, key -> new LinkedHashMap<>())
                    .put(entityId, new Entry(entityId, value, this.now, this.now));
        }

        @Override
        public int id() {
            return this.id;
        }

        @Override
        public WiredExtraVariableWebApi.Access authenticate(byte[] keyHash) {
            return this.keys.authenticate(keyHash);
        }

        @Override
        public boolean boxUsable() {
            return this.usable;
        }

        @Override
        public boolean bulkDeleteAllowed() {
            return this.bulk;
        }

        @Override
        public List<Variable> variables() {
            return this.variables;
        }

        @Override
        public boolean holderExists(TargetKind kind, int entityId) {
            return switch (kind) {
                case USERS -> this.users.containsKey(entityId);
                case PETS -> this.pets.containsKey(entityId);
                case BOTS -> this.bots.containsKey(entityId);
                case FLOOR -> Boolean.TRUE.equals(this.furni.get(entityId));
                case WALL -> Boolean.FALSE.equals(this.furni.get(entityId));
            };
        }

        @Override
        public String holderName(TargetKind kind, int entityId) {
            return switch (kind) {
                case USERS -> this.users.get(entityId);
                case PETS -> this.pets.get(entityId);
                case BOTS -> this.bots.get(entityId);
                default -> null;
            };
        }

        @Override
        public int userIdByName(String username) {
            for (Map.Entry<Integer, String> user : this.users.entrySet()) {
                if (user.getValue().equals(username)) {
                    return user.getKey();
                }
            }
            return 0;
        }

        @Override
        public Entry entry(Variable variable, TargetKind kind, int entityId) {
            if (!this.holderExists(kind, entityId)) {
                return null;
            }
            Map<Integer, Entry> held = this.values.get(variable.name());
            return held == null ? null : held.get(storeKey(kind, entityId));
        }

        @Override
        public List<Entry> holders(Variable variable, TargetKind kind) {
            List<Entry> entries = new ArrayList<>();
            for (Map.Entry<Integer, Entry> stored :
                    this.values.getOrDefault(variable.name(), Map.of()).entrySet()) {
                Entry entry = stored.getValue();
                if (storeKey(kind, entry.entityId()) == stored.getKey() && this.holderExists(kind, entry.entityId())) {
                    entries.add(entry);
                }
            }
            return entries;
        }

        @Override
        public Entry global(Variable variable) {
            return new Entry(this.id, variable.hasValue() ? this.globals.get(variable.name()) : null, 0, this.now);
        }

        @Override
        public boolean assign(Variable variable, TargetKind kind, int entityId, Integer value) {
            this.writes++;
            if (!this.holderExists(kind, entityId)) {
                return false;
            }
            this.holdAs(kind, variable.name(), entityId, variable.hasValue() ? value : null);
            return true;
        }

        @Override
        public boolean update(Variable variable, TargetKind kind, int entityId, int value) {
            this.writes++;
            Entry entry = this.entry(variable, kind, entityId);
            if (entry == null) {
                return false;
            }
            this.values
                    .get(variable.name())
                    .put(storeKey(kind, entityId), new Entry(entityId, value, entry.createdAt(), this.now));
            return true;
        }

        @Override
        public boolean remove(Variable variable, TargetKind kind, int entityId) {
            this.writes++;
            Map<Integer, Entry> held = this.values.get(variable.name());
            return held != null && held.remove(storeKey(kind, entityId)) != null;
        }

        @Override
        public boolean updateGlobal(Variable variable, int value) {
            this.writes++;
            this.globals.put(variable.name(), value);
            return true;
        }

        @Override
        public int clearAll(Variable variable) {
            this.writes++;
            if (variable.scope() == Scope.GLOBAL) {
                this.globals.put(variable.name(), 0);
                return 1;
            }
            Map<Integer, Entry> held = this.values.remove(variable.name());
            return held == null ? 0 : held.size();
        }
    }
}
