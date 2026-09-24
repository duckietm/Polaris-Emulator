package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredWebApiOwnership;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableWrites;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The hotel's rooms as the variables web API sees them. Reads and writes go through the same
 * catalog and write helpers as the wired creator tools, under the "Variable Web API" change origin.
 */
final class HotelWiredApiRooms implements WiredApiRooms {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotelWiredApiRooms.class);
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,40}");

    private static final String STORED_BOX_SQL = "SELECT items.id, items.user_id, items.wired_data FROM items "
            + "INNER JOIN items_base ON items_base.id = items.item_id "
            + "WHERE items.room_id = ? AND items_base.interaction_type = ? LIMIT 4";

    private final Cache<Integer, List<WiredExtraVariableWebApi.KeyState>> storedKeys = Caffeine.newBuilder()
            .maximumSize(16_384)
            .expireAfterWrite(Duration.ofSeconds(10))
            .build();

    @Override
    public VariableRoom loadedRoom(int roomId) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getRoomManager() == null) {
            return null;
        }
        Room room = environment.getRoomManager().getRoom(roomId);
        return room != null && room.isLoaded() ? new HotelRoom(room) : null;
    }

    @Override
    public VariableRoom loadRoom(int roomId) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getRoomManager() == null) {
            return null;
        }
        Room room = environment.getRoomManager().loadRoom(roomId, true);
        return room != null && room.isLoaded() ? new HotelRoom(room) : null;
    }

    @Override
    public List<WiredExtraVariableWebApi.KeyState> storedKeys(int roomId) {
        return this.storedKeys.get(roomId, HotelWiredApiRooms::readStoredKeys);
    }

    private static List<WiredExtraVariableWebApi.KeyState> readStoredKeys(int roomId) {
        Database database = WiredPlatform.database();
        if (database == null) {
            return List.of();
        }
        List<WiredExtraVariableWebApi.KeyState> keys = new ArrayList<>();
        try (Connection connection = database.getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(STORED_BOX_SQL)) {
            statement.setInt(1, roomId);
            statement.setString(2, WiredExtraVariableWebApi.INTERACTION_TYPE);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    WiredExtraVariableWebApi.KeyState state = WiredExtraVariableWebApi.parseStored(
                            set.getString("wired_data"), set.getInt("id"), set.getInt("user_id"));
                    if (state.hasKeys()) {
                        keys.add(state);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to read the web API box of room {}", roomId, e);
        }
        return List.copyOf(keys);
    }

    /** Whether a definition box is a permanent, non-array custom variable of the given scope. */
    static boolean isApiDefinition(InteractionWiredExtra extra, Scope scope) {
        return switch (scope) {
            case USER ->
                extra instanceof WiredExtraUserVariable user && user.isPermanentAvailability() && !user.isArray();
            case FURNI ->
                extra instanceof WiredExtraFurniVariable furni && furni.isPermanentAvailability() && !furni.isArray();
            case GLOBAL ->
                extra instanceof WiredExtraRoomVariable global && global.isPermanentAvailability() && !global.isArray();
        };
    }

    static final class HotelRoom implements VariableRoom {
        private final Room room;
        private final WiredExtraVariableWebApi box;
        private List<Variable> variables;

        HotelRoom(Room room) {
            this.room = room;
            this.box = WiredWebApiOwnership.boxIn(room);
        }

        @Override
        public int id() {
            return this.room.getId();
        }

        @Override
        public WiredExtraVariableWebApi.Access authenticate(byte[] keyHash) {
            return this.box == null ? null : this.box.authenticate(keyHash);
        }

        @Override
        public boolean boxUsable() {
            return this.box != null && this.box.isUsableIn(this.room);
        }

        @Override
        public boolean bulkDeleteAllowed() {
            return this.box != null && this.box.isBulkDeleteAllowed();
        }

        @Override
        public List<Variable> variables() {
            if (this.variables == null) {
                this.variables = List.copyOf(this.readVariables());
            }
            return this.variables;
        }

        private List<Variable> readVariables() {
            List<Variable> variables = new ArrayList<>();
            if (this.room.getRoomSpecialTypes() == null) {
                return variables;
            }
            for (RoomWiredVariableCatalog.Variable variable : RoomWiredVariableCatalog.variables(this.room)) {
                Scope scope = scopeOf(variable.getVariableTarget());
                if (scope == null
                        || variable.isReadOnly()
                        || variable.getVariableName() == null
                        || !NAME.matcher(variable.getVariableName()).matches()) {
                    continue;
                }
                int definitionItemId = RoomWiredVariableCatalog.definitionIdOf(variable.getVariableId());
                if (!isApiDefinition(this.room.getRoomSpecialTypes().getExtra(definitionItemId), scope)) {
                    continue;
                }
                variables.add(new Variable(
                        variable.getVariableName(),
                        scope,
                        definitionItemId,
                        variable.hasValue(),
                        variable.isTextConnected()));
            }
            return variables;
        }

        private static Scope scopeOf(int target) {
            return switch (target) {
                case RoomWiredVariableCatalog.TARGET_USER -> Scope.USER;
                case RoomWiredVariableCatalog.TARGET_FURNI -> Scope.FURNI;
                case RoomWiredVariableCatalog.TARGET_ROOM -> Scope.GLOBAL;
                default -> null;
            };
        }

        private static int writeTarget(Scope scope) {
            return switch (scope) {
                case USER -> RoomWiredVariableWrites.TARGET_USER;
                case FURNI -> RoomWiredVariableWrites.TARGET_FURNI;
                case GLOBAL -> RoomWiredVariableWrites.TARGET_ROOM;
            };
        }

        private static String catalogId(Variable variable) {
            int target =
                    switch (variable.scope()) {
                        case USER -> RoomWiredVariableCatalog.TARGET_USER;
                        case FURNI -> RoomWiredVariableCatalog.TARGET_FURNI;
                        case GLOBAL -> RoomWiredVariableCatalog.TARGET_ROOM;
                    };
            return RoomWiredVariableCatalog.variableId(target, variable.definitionItemId());
        }

        @Override
        public boolean holderExists(TargetKind kind, int entityId) {
            return switch (kind) {
                case USERS -> this.room.getHabbo(entityId) != null;
                case FLOOR, WALL -> this.furni(kind, entityId) != null;
                default -> false;
            };
        }

        private HabboItem furni(TargetKind kind, int entityId) {
            HabboItem item = this.room.getHabboItem(entityId);
            if (item == null || item.getBaseItem() == null) {
                return null;
            }
            FurnitureType type = kind == TargetKind.WALL ? FurnitureType.WALL : FurnitureType.FLOOR;
            return item.getBaseItem().getType() == type ? item : null;
        }

        @Override
        public String holderName(TargetKind kind, int entityId) {
            if (kind == TargetKind.USERS) {
                Habbo habbo = this.room.getHabbo(entityId);
                return habbo != null && habbo.getHabboInfo() != null
                        ? habbo.getHabboInfo().getUsername()
                        : null;
            }
            HabboItem item = this.furni(kind, entityId);
            return item != null ? item.getBaseItem().getName() : null;
        }

        @Override
        public int userIdByName(String username) {
            Habbo habbo = this.room.getHabbo(username);
            return habbo != null && habbo.getHabboInfo() != null
                    ? habbo.getHabboInfo().getId()
                    : 0;
        }

        @Override
        public Entry entry(Variable variable, TargetKind kind, int entityId) {
            int definition = variable.definitionItemId();
            if (variable.scope() == Scope.USER) {
                RoomUserVariableManager users = this.room.getUserVariableManager();
                if (kind != TargetKind.USERS || !users.hasVariable(entityId, definition)) {
                    return null;
                }
                return new Entry(
                        entityId,
                        variable.hasValue() ? users.getCurrentValue(entityId, definition) : null,
                        users.getCreatedAt(entityId, definition),
                        users.getUpdatedAt(entityId, definition));
            }
            RoomFurniVariableManager furni = this.room.getFurniVariableManager();
            if (this.furni(kind, entityId) == null || !furni.hasVariable(entityId, definition)) {
                return null;
            }
            return new Entry(
                    entityId,
                    variable.hasValue() ? furni.getCurrentValue(entityId, definition) : null,
                    furni.getCreatedAt(entityId, definition),
                    furni.getUpdatedAt(entityId, definition));
        }

        @Override
        public List<Entry> holders(Variable variable, TargetKind kind) {
            List<Entry> entries = new ArrayList<>();
            for (RoomWiredVariableCatalog.Holder holder :
                    RoomWiredVariableCatalog.holders(this.room, catalogId(variable), true)) {
                if (variable.scope() == Scope.FURNI && this.furni(kind, holder.getEntityId()) == null) {
                    continue;
                }
                entries.add(new Entry(
                        holder.getEntityId(),
                        variable.hasValue() && holder.hasValue() ? holder.getValue() : null,
                        holder.getCreatedAt() / 1000L,
                        holder.getUpdatedAt() / 1000L));
            }
            return entries;
        }

        @Override
        public Entry global(Variable variable) {
            RoomVariableManager globals = this.room.getRoomVariableManager();
            int definition = variable.definitionItemId();
            return new Entry(
                    this.room.getId(),
                    variable.hasValue() ? globals.getCurrentValue(definition) : null,
                    globals.getCreatedAt(definition),
                    globals.getUpdatedAt(definition));
        }

        @Override
        public boolean assign(Variable variable, TargetKind kind, int entityId, Integer value) {
            int target = writeTarget(variable.scope());
            int written = value == null ? 0 : value;
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.assign(
                            this.room, target, entityId, variable.definitionItemId(), written));
        }

        @Override
        public boolean update(Variable variable, TargetKind kind, int entityId, int value) {
            int target = writeTarget(variable.scope());
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.update(
                            this.room, target, entityId, variable.definitionItemId(), value));
        }

        @Override
        public boolean remove(Variable variable, TargetKind kind, int entityId) {
            int target = writeTarget(variable.scope());
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.remove(this.room, target, entityId, variable.definitionItemId()));
        }

        @Override
        public boolean updateGlobal(Variable variable, int value) {
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.update(
                            this.room, RoomWiredVariableWrites.TARGET_ROOM, 0, variable.definitionItemId(), value));
        }

        @Override
        public int clearAll(Variable variable) {
            if (variable.scope() == Scope.GLOBAL) {
                return WiredVariableChangeOrigin.call(
                                WiredVariableChangeOrigin.WEB_API,
                                () -> RoomWiredVariableWrites.remove(
                                        this.room, RoomWiredVariableWrites.TARGET_ROOM, 0, variable.definitionItemId()))
                        ? 1
                        : 0;
            }
            int target = writeTarget(variable.scope());
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.clearAll(this.room, target, variable.definitionItemId()));
        }
    }
}
