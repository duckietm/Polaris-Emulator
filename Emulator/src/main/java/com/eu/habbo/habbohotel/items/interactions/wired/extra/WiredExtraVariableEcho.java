package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.util.HotelDateTimeUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;

public class WiredExtraVariableEcho extends InteractionWiredExtra {
    public static final int CODE = 83;
    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_ROOM = 3;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";
    private static final int DEFAULT_USER_AVAILABILITY = WiredExtraUserVariable.AVAILABILITY_ROOM;
    private static final int DEFAULT_FURNI_AVAILABILITY = WiredExtraFurniVariable.AVAILABILITY_ROOM_ACTIVE;
    private static final int DEFAULT_ROOM_AVAILABILITY = WiredExtraRoomVariable.AVAILABILITY_ROOM_ACTIVE;

    private String variableName = "";
    private int sourceTargetType = TARGET_USER;
    private String sourceVariableToken = "";
    private int sourceVariableItemId = 0;
    private String sourceVariableName = "";

    public WiredExtraVariableEcho(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableEcho(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        ConfigData config = parseConfigData(settings.getStringParam());
        int normalizedTargetType = normalizeTargetType(config.sourceTargetType);
        String normalizedToken = normalizeVariableToken(config.sourceVariableToken, config.sourceVariableItemId);
        int normalizedItemId = getCustomVariableItemId(normalizedToken);
        SourceState sourceState = this.resolveSourceState(room, normalizedTargetType, normalizedToken, normalizedItemId);

        if (normalizedToken.isEmpty()) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        if (sourceState == null || !sourceState.hasValue()) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        if (!isAllowedEchoSource(sourceState, normalizedToken)) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        if (!WiredVariableTextConnectorSupport.isTextConnected(room, this)) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        if (createsCycle(room, this.getId(), normalizedTargetType, normalizedToken, normalizedItemId)) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        String normalizedName = deriveVariableName(config.variableName, sourceState.getName());
        WiredVariableNameValidator.validateDefinitionName(room, this.getId(), normalizedName);

        this.variableName = normalizedName;
        this.sourceTargetType = normalizedTargetType;
        this.sourceVariableToken = normalizedToken;
        this.sourceVariableItemId = normalizedItemId;
        this.sourceVariableName = sourceState.getName();

        room.getUserVariableManager().broadcastSnapshot();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableName, this.sourceTargetType, this.sourceVariableToken, this.sourceVariableItemId, this.sourceVariableName));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new EditorPayload(this.variableName, this.sourceTargetType, this.sourceVariableToken, this.sourceVariableItemId, this.getResolvedSourceName(room))));
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
        this.sourceTargetType = normalizeTargetType(data.sourceTargetType);
        this.sourceVariableToken = normalizeVariableToken(data.sourceVariableToken, data.sourceVariableItemId);
        this.sourceVariableItemId = getCustomVariableItemId(this.sourceVariableToken);
        this.sourceVariableName = normalizeSourceName(data.sourceVariableName);
    }

    @Override
    public void onPickUp() {
        this.variableName = "";
        this.sourceTargetType = TARGET_USER;
        this.sourceVariableToken = "";
        this.sourceVariableItemId = 0;
        this.sourceVariableName = "";
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public int getSourceTargetType() {
        return this.sourceTargetType;
    }

    public String getSourceVariableToken() {
        return this.sourceVariableToken;
    }

    public int getSourceVariableItemId() {
        return this.sourceVariableItemId;
    }

    public String getSourceVariableName() {
        return this.sourceVariableName;
    }

    public boolean isUserEcho() {
        return this.sourceTargetType == TARGET_USER;
    }

    public boolean isFurniEcho() {
        return this.sourceTargetType == TARGET_FURNI;
    }

    public boolean isRoomEcho() {
        return this.sourceTargetType == TARGET_ROOM;
    }

    public WiredVariableDefinitionInfo createDefinitionInfo(Room room) {
        SourceState sourceState = this.resolveSourceState(room, this.sourceTargetType, this.sourceVariableToken, this.sourceVariableItemId);
        int availability = (sourceState != null) ? sourceState.getAvailability() : defaultAvailability(this.sourceTargetType);
        boolean hasValue = (sourceState == null) || sourceState.hasValue();
        boolean readOnly = sourceState == null || sourceState.isReadOnly();

        return new WiredVariableDefinitionInfo(
            this.getId(),
            this.variableName,
            hasValue,
            availability,
            WiredVariableTextConnectorSupport.isTextConnected(room, this),
            readOnly
        );
    }

    public boolean hasVariable(Room room, int entityId) {
        if (room == null) {
            return false;
        }

        if (isCustomVariableToken(this.sourceVariableToken)) {
            return switch (this.sourceTargetType) {
                case TARGET_FURNI -> room.getFurniVariableManager().hasVariable(entityId, this.sourceVariableItemId);
                case TARGET_ROOM -> room.getRoomVariableManager().hasVariable(this.sourceVariableItemId);
                default -> room.getUserVariableManager().hasVariable(entityId, this.sourceVariableItemId);
            };
        }

        return this.readCurrentValue(room, entityId) != null;
    }

    public int getCurrentValue(Room room, int entityId) {
        Integer value = this.readCurrentValue(room, entityId);
        return (value != null) ? value : 0;
    }

    public int getCreatedAt(Room room, int entityId) {
        if (room == null || !isCustomVariableToken(this.sourceVariableToken)) {
            return 0;
        }

        return switch (this.sourceTargetType) {
            case TARGET_FURNI -> room.getFurniVariableManager().getCreatedAt(entityId, this.sourceVariableItemId);
            case TARGET_ROOM -> room.getRoomVariableManager().getCreatedAt(this.sourceVariableItemId);
            default -> room.getUserVariableManager().getCreatedAt(entityId, this.sourceVariableItemId);
        };
    }

    public int getUpdatedAt(Room room, int entityId) {
        if (room == null || !isCustomVariableToken(this.sourceVariableToken)) {
            return 0;
        }

        return switch (this.sourceTargetType) {
            case TARGET_FURNI -> room.getFurniVariableManager().getUpdatedAt(entityId, this.sourceVariableItemId);
            case TARGET_ROOM -> room.getRoomVariableManager().getUpdatedAt(this.sourceVariableItemId);
            default -> room.getUserVariableManager().getUpdatedAt(entityId, this.sourceVariableItemId);
        };
    }

    public boolean assignValue(Room room, int entityId, Integer value, boolean overrideExisting) {
        if (room == null) {
            return false;
        }

        SourceState sourceState = this.resolveSourceState(room, this.sourceTargetType, this.sourceVariableToken, this.sourceVariableItemId);
        if (sourceState == null || sourceState.isReadOnly()) {
            return false;
        }

        if (isCustomVariableToken(this.sourceVariableToken)) {
            return switch (this.sourceTargetType) {
                case TARGET_FURNI -> room.getFurniVariableManager().assignVariable(room.getHabboItem(entityId), this.sourceVariableItemId, value, overrideExisting);
                case TARGET_ROOM -> room.getRoomVariableManager().updateVariableValue(this.sourceVariableItemId, (value != null) ? value : 0);
                default -> room.getUserVariableManager().assignVariable(room.getHabbo(entityId), this.sourceVariableItemId, value, overrideExisting);
            };
        }

        return value != null && this.writeCurrentValue(room, entityId, value);
    }

    public boolean updateValue(Room room, int entityId, Integer value) {
        if (room == null) {
            return false;
        }

        SourceState sourceState = this.resolveSourceState(room, this.sourceTargetType, this.sourceVariableToken, this.sourceVariableItemId);
        if (sourceState == null || sourceState.isReadOnly() || !sourceState.hasValue()) {
            return false;
        }

        if (isCustomVariableToken(this.sourceVariableToken)) {
            return switch (this.sourceTargetType) {
                case TARGET_FURNI -> room.getFurniVariableManager().updateVariableValue(entityId, this.sourceVariableItemId, value);
                case TARGET_ROOM -> room.getRoomVariableManager().updateVariableValue(this.sourceVariableItemId, (value != null) ? value : 0);
                default -> room.getUserVariableManager().updateVariableValue(entityId, this.sourceVariableItemId, value);
            };
        }

        return value != null && this.writeCurrentValue(room, entityId, value);
    }

    public boolean removeValue(Room room, int entityId) {
        if (room == null || !isCustomVariableToken(this.sourceVariableToken)) {
            return false;
        }

        SourceState sourceState = this.resolveSourceState(room, this.sourceTargetType, this.sourceVariableToken, this.sourceVariableItemId);
        if (sourceState == null || sourceState.isReadOnly()) {
            return false;
        }

        return switch (this.sourceTargetType) {
            case TARGET_FURNI -> room.getFurniVariableManager().removeVariable(entityId, this.sourceVariableItemId);
            case TARGET_ROOM -> room.getRoomVariableManager().removeVariable(this.sourceVariableItemId);
            default -> room.getUserVariableManager().removeVariable(entityId, this.sourceVariableItemId);
        };
    }

    private Integer readCurrentValue(Room room, int entityId) {
        if (room == null || this.sourceVariableToken == null || this.sourceVariableToken.isEmpty()) {
            return null;
        }

        if (isCustomVariableToken(this.sourceVariableToken)) {
            return switch (this.sourceTargetType) {
                case TARGET_FURNI -> room.getFurniVariableManager().hasVariable(entityId, this.sourceVariableItemId)
                    ? room.getFurniVariableManager().getCurrentValue(entityId, this.sourceVariableItemId)
                    : null;
                case TARGET_ROOM -> room.getRoomVariableManager().getCurrentValue(this.sourceVariableItemId);
                default -> room.getUserVariableManager().hasVariable(entityId, this.sourceVariableItemId)
                    ? room.getUserVariableManager().getCurrentValue(entityId, this.sourceVariableItemId)
                    : null;
            };
        }

        String key = getInternalVariableKey(this.sourceVariableToken);
        if (key.isEmpty()) {
            return null;
        }

        return switch (this.sourceTargetType) {
            case TARGET_FURNI -> this.readFurniInternalValue(room, room.getHabboItem(entityId), key);
            case TARGET_ROOM -> this.readRoomInternalValue(room, key);
            default -> {
                Habbo habbo = room.getHabbo(entityId);
                yield this.readUserInternalValue(room, (habbo != null) ? habbo.getRoomUnit() : null, key);
            }
        };
    }

    private boolean writeCurrentValue(Room room, int entityId, int value) {
        if (room == null || !isInternalVariableToken(this.sourceVariableToken)) {
            return false;
        }

        String key = getInternalVariableKey(this.sourceVariableToken);
        if (key.isEmpty()) {
            return false;
        }

        return switch (this.sourceTargetType) {
            case TARGET_FURNI -> this.writeFurniInternalValue(room, room.getHabboItem(entityId), key, value);
            case TARGET_ROOM -> false;
            default -> {
                Habbo habbo = room.getHabbo(entityId);
                yield this.writeUserInternalValue(room, (habbo != null) ? habbo.getRoomUnit() : null, key, value);
            }
        };
    }

    private SourceState resolveSourceState(Room room, int targetType, String token, int variableItemId) {
        if (room == null || token == null || token.isEmpty()) {
            return null;
        }

        if (isCustomVariableToken(token)) {
            WiredVariableDefinitionInfo definitionInfo = switch (targetType) {
                case TARGET_FURNI -> room.getFurniVariableManager().getDefinitionInfo(variableItemId);
                case TARGET_ROOM -> room.getRoomVariableManager().getDefinitionInfo(variableItemId);
                default -> room.getUserVariableManager().getDefinitionInfo(variableItemId);
            };

            if (definitionInfo == null) {
                return null;
            }

            return new SourceState(definitionInfo.getName(), definitionInfo.hasValue(), definitionInfo.getAvailability(), definitionInfo.isReadOnly());
        }

        String key = getInternalVariableKey(token);
        if (key.isEmpty()) {
            return null;
        }

        return switch (targetType) {
            case TARGET_FURNI -> canUseFurniInternalReference(key)
                ? new SourceState(key, true, DEFAULT_FURNI_AVAILABILITY, !canUseFurniInternalDestination(key))
                : null;
            case TARGET_ROOM -> canUseRoomInternalReference(key)
                ? new SourceState(key, true, DEFAULT_ROOM_AVAILABILITY, true)
                : null;
            default -> canUseUserInternalReference(key)
                ? new SourceState(key, true, DEFAULT_USER_AVAILABILITY, !canUseUserInternalDestination(key))
                : null;
        };
    }

    private String getResolvedSourceName(Room room) {
        SourceState sourceState = this.resolveSourceState(room, this.sourceTargetType, this.sourceVariableToken, this.sourceVariableItemId);
        return (sourceState != null) ? sourceState.getName() : this.sourceVariableName;
    }

    private static boolean createsCycle(Room room, int currentItemId, int targetType, String token, int variableItemId) {
        if (room == null || currentItemId <= 0 || !isCustomVariableToken(token) || variableItemId <= 0) {
            return false;
        }

        if (variableItemId == currentItemId) {
            return true;
        }

        WiredVariableLevelSystemSupport.DerivedDefinition derivedDefinition = WiredVariableLevelSystemSupport.resolveDerivedDefinition(room, targetType, variableItemId);
        if (derivedDefinition != null) {
            return createsCycle(room, currentItemId, targetType, createCustomVariableToken(derivedDefinition.getBaseDefinitionItemId()), derivedDefinition.getBaseDefinitionItemId());
        }

        if (room.getRoomSpecialTypes() == null) {
            return false;
        }

        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(variableItemId);
        if (!(extra instanceof WiredExtraVariableEcho)) {
            return false;
        }

        WiredExtraVariableEcho echo = (WiredExtraVariableEcho) extra;
        return createsCycle(room, currentItemId, echo.getSourceTargetType(), echo.getSourceVariableToken(), echo.getSourceVariableItemId());
    }

    private static String deriveVariableName(String requestedName, String sourceName) {
        String normalizedRequestedName = WiredVariableNameValidator.normalizeForSave(requestedName);
        if (!normalizedRequestedName.isEmpty()) {
            return normalizedRequestedName;
        }

        String fallbackValue = normalizeSourceName(sourceName)
            .replaceAll("^[~@]+", "")
            .replaceAll("[^A-Za-z0-9_]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");

        if (fallbackValue.length() > WiredVariableNameValidator.MAX_NAME_LENGTH) {
            fallbackValue = fallbackValue.substring(0, WiredVariableNameValidator.MAX_NAME_LENGTH);
        }

        return fallbackValue;
    }

    private static boolean isAllowedEchoSource(SourceState sourceState, String token) {
        if (sourceState == null || token == null || token.isEmpty()) {
            return false;
        }

        if (isInternalVariableToken(token)) {
            return true;
        }

        return isCustomVariableToken(token) && sourceState.getName() != null && sourceState.getName().contains(".");
    }

    private static ConfigData parseConfigData(String value) {
        if (value == null || value.isEmpty() || !value.startsWith("{")) {
            return new ConfigData();
        }

        ConfigData config = WiredExtraPayloadGuard.fromJson(value, ConfigData.class);
        return (config != null) ? config : new ConfigData();
    }

    private static String normalizeSourceName(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replace("\t", "").replace("\r", "").replace("\n", "");
    }

    private static int normalizeTargetType(int value) {
        if (value == TARGET_FURNI || value == TARGET_ROOM) {
            return value;
        }

        return TARGET_USER;
    }

    private static int defaultAvailability(int targetType) {
        return switch (targetType) {
            case TARGET_FURNI -> DEFAULT_FURNI_AVAILABILITY;
            case TARGET_ROOM -> DEFAULT_ROOM_AVAILABILITY;
            default -> DEFAULT_USER_AVAILABILITY;
        };
    }

    private static boolean isCustomVariableToken(String token) {
        return token != null && token.startsWith(CUSTOM_TOKEN_PREFIX);
    }

    private static boolean isInternalVariableToken(String token) {
        return token != null && token.startsWith(INTERNAL_TOKEN_PREFIX);
    }

    private static String getInternalVariableKey(String token) {
        return isInternalVariableToken(token) ? WiredInternalVariableSupport.normalizeKey(token.substring(INTERNAL_TOKEN_PREFIX.length())) : "";
    }

    private static int getCustomVariableItemId(String token) {
        if (!isCustomVariableToken(token)) {
            return 0;
        }

        try {
            return Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String createCustomVariableToken(int itemId) {
        return itemId > 0 ? CUSTOM_TOKEN_PREFIX + itemId : "";
    }

    private static String normalizeVariableToken(String token, int fallbackItemId) {
        String normalizedToken = (token != null) ? token.trim() : "";

        if (isCustomVariableToken(normalizedToken)) {
            return normalizedToken;
        }

        if (isInternalVariableToken(normalizedToken)) {
            return INTERNAL_TOKEN_PREFIX + WiredInternalVariableSupport.normalizeKey(normalizedToken.substring(INTERNAL_TOKEN_PREFIX.length()));
        }

        if (fallbackItemId > 0) {
            return createCustomVariableToken(fallbackItemId);
        }

        if (normalizedToken.isEmpty()) {
            return "";
        }

        try {
            return createCustomVariableToken(Integer.parseInt(normalizedToken));
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private Integer readUserInternalValue(Room room, RoomUnit roomUnit, String key) {
        return WiredInternalVariableSupport.readUserValue(room, roomUnit, key);
    }

    private Integer getUserTypeValue(Habbo habbo, Bot bot, Pet pet) {
        if (habbo != null) return 1;
        if (bot != null) return 2;
        if (pet != null) return 3;

        return null;
    }

    private Integer getGenderValue(Habbo habbo, Bot bot) {
        HabboGender gender = null;

        if (habbo != null && habbo.getHabboInfo() != null) {
            gender = habbo.getHabboInfo().getGender();
        } else if (bot != null) {
            gender = bot.getGender();
        }

        if (gender == null) {
            return null;
        }

        return gender == HabboGender.F ? 1 : 2;
    }

    private boolean writeUserInternalValue(Room room, RoomUnit roomUnit, String key, int value) {
        return WiredInternalVariableSupport.writeUserValue(room, roomUnit, key, value);
    }

    private Integer readFurniInternalValue(Room room, HabboItem item, String key) {
        return WiredInternalVariableSupport.readFurniValue(room, item, key);
    }

    private boolean writeFurniInternalValue(Room room, HabboItem item, String key, int value) {
        return WiredInternalVariableSupport.writeFurniValue(room, item, key, value);
    }

    private Integer readRoomInternalValue(Room room, String key) {
        return WiredInternalVariableSupport.readRoomValue(room, key);
    }

    private Integer getUserTeamScore(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) return null;

        Game game = this.resolveTeamGame(room, habbo);
        GamePlayer gamePlayer = habbo.getHabboInfo().getGamePlayer();

        if (game == null || gamePlayer.getTeamColor() == null) return gamePlayer.getScore();

        GameTeam team = game.getTeam(gamePlayer.getTeamColor());
        return (team != null) ? team.getTotalScore() : gamePlayer.getScore();
    }

    private int getTeamMetric(Room room, GameTeamColors color, boolean score) {
        Game game = this.resolveTeamGame(room, null);
        if (game == null || color == null) return 0;

        GameTeam team = game.getTeam(color);
        if (team == null) return 0;

        return score ? team.getTotalScore() : team.getMembers().size();
    }

    private int getTeamColorId(int effectId) {
        if (effectId >= 33 && effectId <= 36) return effectId - 32;
        if (effectId >= 40 && effectId <= 43) return effectId - 39;
        return 0;
    }

    private int getTeamTypeId(int effectId) {
        if (effectId >= 33 && effectId <= 36) return 1;
        if (effectId >= 40 && effectId <= 43) return 2;
        return 0;
    }

    private Game resolveTeamGame(Room room, Habbo habbo) {
        if (room == null) return null;

        if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getCurrentGame() != null) {
            Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());
            if (game != null) return game;
        }

        Game game = room.getGame(com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame.class);
        if (game != null) return game;

        game = room.getGame(com.eu.habbo.habbohotel.games.freeze.FreezeGame.class);
        if (game != null) return game;

        return room.getGame(com.eu.habbo.habbohotel.games.wired.WiredGame.class);
    }

    private boolean moveUserTo(Room room, RoomUnit roomUnit, int x, int y) {
        if (room == null || roomUnit == null || room.getLayout() == null) return false;

        RoomTile targetTile = room.getLayout().getTile((short) x, (short) y);
        if (targetTile == null || targetTile.state == RoomTileState.INVALID) return false;

        double targetZ = WiredUserMovementHelper.resolveUserTargetZ(room, targetTile);
        return WiredUserMovementHelper.moveUser(
            room,
            roomUnit,
            targetTile,
            targetZ,
            roomUnit.getBodyRotation(),
            roomUnit.getHeadRotation(),
            WiredUserMovementHelper.DEFAULT_ANIMATION_DURATION,
            false);
    }

    private boolean moveFurniTo(Room room, HabboItem item, int x, int y, int rotation, double z) {
        if (room == null || item == null || room.getLayout() == null) return false;

        RoomTile targetTile = room.getLayout().getTile((short) x, (short) y);
        if (targetTile == null || targetTile.state == RoomTileState.INVALID) return false;

        FurnitureMovementError error = room.moveFurniTo(item, targetTile, rotation, z, null, true, true);
        return error == FurnitureMovementError.NONE;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return 0;

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean canUseUserInternalDestination(String key) {
        return WiredInternalVariableSupport.canUseUserDestination(key);
    }

    private static boolean canUseFurniInternalDestination(String key) {
        return WiredInternalVariableSupport.canUseFurniDestination(key);
    }

    private static boolean canUseUserInternalReference(String key) {
        return WiredInternalVariableSupport.canUseUserReference(key);
    }

    private static boolean canUseFurniInternalReference(String key) {
        return WiredInternalVariableSupport.canUseFurniReference(key);
    }

    private static boolean canUseRoomInternalReference(String key) {
        return WiredInternalVariableSupport.canUseRoomReference(key);
    }

    private Integer getRoomEntryMethodValue(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return null;
        }

        String roomEntryMethod = habbo.getHabboInfo().getRoomEntryMethod();

        if (roomEntryMethod == null || roomEntryMethod.trim().isEmpty()) {
            return 0;
        }

        return switch (roomEntryMethod.trim().toLowerCase(Locale.ROOT)) {
            case "door" -> 1;
            case "teleport" -> 2;
            default -> 3;
        };
    }

    static class JsonData {
        String variableName;
        int sourceTargetType;
        String sourceVariableToken;
        int sourceVariableItemId;
        String sourceVariableName;

        JsonData(String variableName, int sourceTargetType, String sourceVariableToken, int sourceVariableItemId, String sourceVariableName) {
            this.variableName = variableName;
            this.sourceTargetType = sourceTargetType;
            this.sourceVariableToken = sourceVariableToken;
            this.sourceVariableItemId = sourceVariableItemId;
            this.sourceVariableName = sourceVariableName;
        }
    }

    static class ConfigData {
        String variableName = "";
        int sourceTargetType = TARGET_USER;
        String sourceVariableToken = "";
        int sourceVariableItemId = 0;
    }

    static class EditorPayload extends ConfigData {
        String sourceVariableName;

        EditorPayload(String variableName, int sourceTargetType, String sourceVariableToken, int sourceVariableItemId, String sourceVariableName) {
            this.variableName = variableName;
            this.sourceTargetType = sourceTargetType;
            this.sourceVariableToken = sourceVariableToken;
            this.sourceVariableItemId = sourceVariableItemId;
            this.sourceVariableName = sourceVariableName;
        }
    }

    private static class SourceState {
        private final String name;
        private final boolean hasValue;
        private final int availability;
        private final boolean readOnly;

        private SourceState(String name, boolean hasValue, int availability, boolean readOnly) {
            this.name = name;
            this.hasValue = hasValue;
            this.availability = availability;
            this.readOnly = readOnly;
        }

        public String getName() {
            return this.name;
        }

        public boolean hasValue() {
            return this.hasValue;
        }

        public int getAvailability() {
            return this.availability;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }
    }
}
