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
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore.Order;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableWrites;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The hotel's rooms as the variables web API sees them. Reads and writes go through the same
 * catalog and write helpers as the wired creator tools, under the "Variable Web API" change origin.
 * User values go through the room's {@link RoomUserVariableStore}: live for users in the room, the
 * saved rows for users who are not.
 */
final class HotelWiredApiRooms implements WiredApiRooms {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotelWiredApiRooms.class);
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,40}");

    private static final String STORED_BOX_SQL = "SELECT items.id, items.user_id, items.wired_data FROM items "
            + "INNER JOIN items_base ON items_base.id = items.item_id "
            + "WHERE items.room_id = ? AND (items_base.interaction_type = ? OR items_base.item_name = ?) LIMIT 4";
    private static final String BUILDERS_CLUB_SQL = "SELECT builders_club_items.item_id FROM builders_club_items "
            + "INNER JOIN items ON items.id = builders_club_items.item_id WHERE items.room_id = ?";

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
        return database == null ? List.of() : readStoredKeys(database.getDataSource(), roomId);
    }

    /**
     * The keys of the boxes stored in a room. A box is found by interaction or by item name, since a
     * {@code wf_} item with interaction {@code default} takes its interaction from its name.
     */
    static List<WiredExtraVariableWebApi.KeyState> readStoredKeys(DataSource dataSource, int roomId) {
        List<WiredExtraVariableWebApi.KeyState> keys = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(STORED_BOX_SQL)) {
            statement.setInt(1, roomId);
            statement.setString(2, WiredExtraVariableWebApi.INTERACTION_TYPE);
            statement.setString(3, WiredExtraVariableWebApi.INTERACTION_TYPE);
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

    /**
     * Whether an item is of a furni kind: floor or wall, and Builders Club or not. Builders Club items
     * carry the virtual owner and are tracked in {@code builders_club_items}; the tracking is only
     * looked up for items with that owner.
     */
    static boolean isOfKind(HabboItem item, TargetKind kind, IntPredicate trackedBuildersClub) {
        if (item == null || item.getBaseItem() == null || kind.scope() != Scope.FURNI) {
            return false;
        }
        FurnitureType type = kind.wall() ? FurnitureType.WALL : FurnitureType.FLOOR;
        if (item.getBaseItem().getType() != type) {
            return false;
        }
        boolean buildersClub =
                item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID && trackedBuildersClub.test(item.getId());
        return buildersClub == kind.buildersClub();
    }

    /** The Builders Club item ids of a room, read once per request and only when an item needs it. */
    private static Set<Integer> readBuildersClubItems(int roomId) {
        Database database = WiredPlatform.database();
        if (database == null) {
            return Set.of();
        }
        Set<Integer> ids = new HashSet<>();
        try (Connection connection = database.getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(BUILDERS_CLUB_SQL)) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    ids.add(set.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to read the Builders Club items of room {}", roomId, e);
        }
        return ids;
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
        private Set<Integer> buildersClubItems;

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

        /** The user-variable key of a user, pet or bot (see {@link UserVariableHolders}), 0 for furni. */
        private static int userKey(TargetKind kind, int entityId) {
            return switch (kind) {
                case USERS -> UserVariableHolders.ofUser(entityId);
                case PETS -> UserVariableHolders.ofPet(entityId);
                case BOTS -> UserVariableHolders.ofBot(entityId);
                default -> 0;
            };
        }

        private static TargetKind kindOf(int key) {
            UserVariableHolders.Kind kind = UserVariableHolders.kindOf(key);
            if (kind == null) {
                return null;
            }
            return switch (kind) {
                case USER -> TargetKind.USERS;
                case PET -> TargetKind.PETS;
                case BOT -> TargetKind.BOTS;
            };
        }

        /** The id the variable managers use for this holder. */
        private static int holderId(Variable variable, TargetKind kind, int entityId) {
            return variable.scope() == Scope.USER ? userKey(kind, entityId) : entityId;
        }

        private RoomUserVariableStore users() {
            return this.room.getUserVariableManager().getStore();
        }

        @Override
        public boolean holderExists(TargetKind kind, int entityId) {
            if (kind == TargetKind.USERS) {
                return this.users().participates(UserVariableHolders.ofUser(entityId));
            }
            return kind.scope() == Scope.USER
                    ? UserVariableHolders.isInRoom(this.room, userKey(kind, entityId))
                    : this.furni(kind, entityId) != null;
        }

        @Override
        public <T> T atomically(TargetKind kind, int entityId, Supplier<T> work) {
            return kind == TargetKind.USERS
                    ? this.users().withUser(UserVariableHolders.ofUser(entityId), work)
                    : work.get();
        }

        private HabboItem furni(TargetKind kind, int entityId) {
            HabboItem item = this.room.getHabboItem(entityId);
            return isOfKind(item, kind, this::isTrackedBuildersClubItem) ? item : null;
        }

        private boolean isTrackedBuildersClubItem(int itemId) {
            if (this.buildersClubItems == null) {
                this.buildersClubItems = readBuildersClubItems(this.room.getId());
            }
            return this.buildersClubItems.contains(itemId);
        }

        @Override
        public String holderName(TargetKind kind, int entityId) {
            if (kind == TargetKind.USERS) {
                return this.users().name(UserVariableHolders.ofUser(entityId));
            }
            if (kind.scope() == Scope.USER) {
                String name = UserVariableHolders.nameOf(this.room, userKey(kind, entityId));
                return name.isEmpty() ? null : name;
            }
            HabboItem item = this.furni(kind, entityId);
            return item != null ? item.getBaseItem().getName() : null;
        }

        @Override
        public int userIdByName(String username) {
            return this.users().userIdByName(username);
        }

        @Override
        public Entry entry(Variable variable, TargetKind kind, int entityId) {
            int definition = variable.definitionItemId();
            if (variable.scope() == Scope.USER && kind == TargetKind.USERS) {
                RoomUserVariableStore.Value value = this.users().get(UserVariableHolders.ofUser(entityId), definition);
                return value == null ? null : entry(variable, entityId, value);
            }
            if (variable.scope() == Scope.USER) {
                RoomUserVariableManager users = this.room.getUserVariableManager();
                int key = userKey(kind, entityId);
                if (key == 0 || !users.hasVariable(key, definition)) {
                    return null;
                }
                return new Entry(
                        entityId,
                        variable.hasValue() ? users.getCurrentValue(key, definition) : null,
                        users.getCreatedAt(key, definition),
                        users.getUpdatedAt(key, definition));
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

        private static Entry entry(Variable variable, int entityId, RoomUserVariableStore.Value value) {
            return new Entry(
                    entityId, variable.hasValue() ? value.value() : null, value.createdAt(), value.updatedAt());
        }

        @Override
        public Map<Variable, Entry> entries(TargetKind kind, int entityId) {
            if (kind != TargetKind.USERS) {
                return VariableRoom.super.entries(kind, entityId);
            }
            List<Integer> definitions = new ArrayList<>();
            for (Variable variable : this.variables()) {
                if (variable.scope() == Scope.USER) {
                    definitions.add(variable.definitionItemId());
                }
            }
            Map<Integer, RoomUserVariableStore.Value> values =
                    this.users().all(UserVariableHolders.ofUser(entityId), definitions);
            Map<Variable, Entry> entries = new LinkedHashMap<>();
            for (Variable variable : this.variables()) {
                RoomUserVariableStore.Value value =
                        variable.scope() == Scope.USER ? values.get(variable.definitionItemId()) : null;
                if (value != null) {
                    entries.put(variable, entry(variable, entityId, value));
                }
            }
            return entries;
        }

        @Override
        public List<Entry> holderPage(
                Variable variable, TargetKind kind, Order order, boolean descending, int offset, int limit) {
            if (kind != TargetKind.USERS) {
                return WiredApiRooms.page(this.holders(variable, kind), order, descending, offset, limit);
            }
            List<Entry> entries = new ArrayList<>();
            for (RoomUserVariableStore.Holder holder :
                    this.users().page(variable.definitionItemId(), order, descending, offset, limit)) {
                entries.add(new Entry(
                        holder.userId(),
                        variable.hasValue() ? holder.value() : null,
                        holder.createdAt(),
                        holder.updatedAt(),
                        holder.name() == null || holder.name().isEmpty() ? null : holder.name()));
            }
            return entries;
        }

        @Override
        public int holderCount(Variable variable, TargetKind kind) {
            return kind == TargetKind.USERS
                    ? this.users().count(variable.definitionItemId())
                    : this.holders(variable, kind).size();
        }

        /** The holders the live stores know, for everything but users. */
        private List<Entry> holders(Variable variable, TargetKind kind) {
            List<Entry> entries = new ArrayList<>();
            for (RoomWiredVariableCatalog.Holder holder :
                    RoomWiredVariableCatalog.holders(this.room, catalogId(variable), true)) {
                if (variable.scope() == Scope.FURNI && this.furni(kind, holder.getEntityId()) == null) {
                    continue;
                }
                if (variable.scope() == Scope.USER && kindOf(holder.getEntityId()) != kind) {
                    continue;
                }
                entries.add(new Entry(
                        variable.scope() == Scope.USER
                                ? UserVariableHolders.idOf(holder.getEntityId())
                                : holder.getEntityId(),
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
            if (kind == TargetKind.USERS) {
                return written(WiredVariableChangeOrigin.call(
                        WiredVariableChangeOrigin.WEB_API,
                        () -> this.users()
                                .put(UserVariableHolders.ofUser(entityId), variable.definitionItemId(), written)));
            }
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.assign(
                            this.room,
                            target,
                            holderId(variable, kind, entityId),
                            variable.definitionItemId(),
                            written));
        }

        @Override
        public boolean update(Variable variable, TargetKind kind, int entityId, int value) {
            if (kind == TargetKind.USERS) {
                return written(WiredVariableChangeOrigin.call(
                        WiredVariableChangeOrigin.WEB_API,
                        () -> this.users()
                                .update(UserVariableHolders.ofUser(entityId), variable.definitionItemId(), value)));
            }
            int target = writeTarget(variable.scope());
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.update(
                            this.room, target, holderId(variable, kind, entityId), variable.definitionItemId(), value));
        }

        @Override
        public boolean remove(Variable variable, TargetKind kind, int entityId) {
            if (kind == TargetKind.USERS) {
                return written(WiredVariableChangeOrigin.call(
                        WiredVariableChangeOrigin.WEB_API,
                        () -> this.users().remove(UserVariableHolders.ofUser(entityId), variable.definitionItemId())));
            }
            int target = writeTarget(variable.scope());
            return WiredVariableChangeOrigin.call(
                    WiredVariableChangeOrigin.WEB_API,
                    () -> RoomWiredVariableWrites.remove(
                            this.room, target, holderId(variable, kind, entityId), variable.definitionItemId()));
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
            // The live clear also deletes the saved rows of users who are not in the room; count them first.
            int absent = variable.scope() == Scope.USER ? this.users().countAbsent(variable.definitionItemId()) : 0;
            return absent
                    + WiredVariableChangeOrigin.call(
                            WiredVariableChangeOrigin.WEB_API,
                            () -> RoomWiredVariableWrites.clearAll(this.room, target, variable.definitionItemId()));
        }

        /** A saved-row write of a variable users do not keep is refused like any write for an absent user. */
        static boolean written(RoomUserVariableStore.Write write) {
            if (write == RoomUserVariableStore.Write.NOT_SAVED) {
                throw WiredApiException.forbidden(
                        WiredApiException.USER_NOT_PARTICIPATING, "The variable is not kept for users who leave.");
            }
            return write == RoomUserVariableStore.Write.WRITTEN;
        }
    }
}
