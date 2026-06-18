package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.util.HotelDateTimeUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class WiredEffectChangeVariableValue extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CHANGE_VAR_VAL;
    public static final int TARGET_USER = 0, TARGET_FURNI = 1, TARGET_CONTEXT = 2, TARGET_ROOM = 3;
    public static final int REF_CONSTANT = 0, REF_VARIABLE = 1;
    public static final int OP_ASSIGN = 0, OP_ADD = 1, OP_SUB = 2, OP_MUL = 3, OP_DIV = 4, OP_POW = 5, OP_MOD = 6, OP_MIN = 40, OP_MAX = 41, OP_RANDOM = 50, OP_ABS = 60, OP_AND = 100, OP_OR = 101, OP_XOR = 102, OP_NOT = 103, OP_LSHIFT = 104, OP_RSHIFT = 105;

    private static final int SOURCE_SECONDARY_SELECTED = 101;
    private static final String DELIM = "\t", FURNI_DELIM = ";";
    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";

    private int destinationTargetType = TARGET_USER, destinationVariableItemId = 0, operation = OP_ASSIGN, referenceMode = REF_CONSTANT, referenceConstantValue = 0, referenceTargetType = TARGET_USER, referenceVariableItemId = 0, destinationUserSource = WiredSourceUtil.SOURCE_TRIGGER, destinationFurniSource = WiredSourceUtil.SOURCE_TRIGGER, referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER, referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private String destinationVariableToken = "", referenceVariableToken = "";
    private final List<HabboItem> destinationSelectedFurni = new ArrayList<>();
    private final List<HabboItem> referenceSelectedFurni = new ArrayList<>();

    public WiredEffectChangeVariableValue(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeVariableValue(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        switch (this.destinationTargetType) {
            case TARGET_USER -> this.executeUsers(ctx, room);
            case TARGET_FURNI -> this.executeFurni(ctx, room);
            case TARGET_CONTEXT -> this.executeContext(ctx, room);
            case TARGET_ROOM -> this.executeRoom(ctx, room);
        }
    }

    private void executeUsers(WiredContext ctx, Room room) {
        if (isInternalVariableToken(this.destinationVariableToken)) {
            this.executeUsersInternal(ctx, room, getInternalVariableKey(this.destinationVariableToken));
            return;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(this.destinationVariableItemId);
        if (definition == null || !definition.hasValue() || definition.isReadOnly()) return;

        ReferenceSnapshot references = this.resolveReferences(ctx, room);
        int index = 0;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.destinationUserSource)) {
            if (roomUnit == null) continue;

            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo == null) continue;

            Integer referenceValue = this.referenceFor(references, roomUnit.getId(), TARGET_USER, index++);
            if (!this.isUnaryOperation() && referenceValue == null) continue;

            int currentValue = room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), this.destinationVariableItemId);
            room.getUserVariableManager().updateVariableValue(habbo.getHabboInfo().getId(), this.destinationVariableItemId, this.applyOperation(currentValue, referenceValue));
        }
    }

    private void executeUsersInternal(WiredContext ctx, Room room, String key) {
        if (!canUseUserInternalDestination(key)) return;

        ReferenceSnapshot references = this.resolveReferences(ctx, room);
        int index = 0;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.destinationUserSource)) {
            if (roomUnit == null) continue;

            Integer currentValue = this.readUserInternalValue(room, roomUnit, key);
            if (currentValue == null) continue;

            Integer referenceValue = this.referenceFor(references, roomUnit.getId(), TARGET_USER, index++);
            if (!this.isUnaryOperation() && referenceValue == null) continue;

            this.writeUserInternalValue(room, roomUnit, key, this.applyOperation(currentValue, referenceValue));
        }
    }

    private void executeFurni(WiredContext ctx, Room room) {
        if (isInternalVariableToken(this.destinationVariableToken)) {
            this.executeFurniInternal(ctx, room, getInternalVariableKey(this.destinationVariableToken));
            return;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(this.destinationVariableItemId);
        if (definition == null || !definition.hasValue() || definition.isReadOnly()) return;
        if (this.destinationFurniSource == WiredSourceUtil.SOURCE_SELECTED) this.validateItems(this.destinationSelectedFurni);

        ReferenceSnapshot references = this.resolveReferences(ctx, room);
        int index = 0;

        for (HabboItem item : WiredSourceUtil.resolveItems(ctx, this.destinationFurniSource, this.destinationSelectedFurni)) {
            if (item == null) continue;

            Integer referenceValue = this.referenceFor(references, item.getId(), TARGET_FURNI, index++);
            if (!this.isUnaryOperation() && referenceValue == null) continue;

            int currentValue = room.getFurniVariableManager().getCurrentValue(item.getId(), this.destinationVariableItemId);
            room.getFurniVariableManager().updateVariableValue(item.getId(), this.destinationVariableItemId, this.applyOperation(currentValue, referenceValue));
        }
    }

    private void executeFurniInternal(WiredContext ctx, Room room, String key) {
        if (!canUseFurniInternalDestination(key)) return;
        if (this.destinationFurniSource == WiredSourceUtil.SOURCE_SELECTED) this.validateItems(this.destinationSelectedFurni);

        ReferenceSnapshot references = this.resolveReferences(ctx, room);
        int index = 0;

        for (HabboItem item : WiredSourceUtil.resolveItems(ctx, this.destinationFurniSource, this.destinationSelectedFurni)) {
            if (item == null) continue;

            Integer currentValue = this.readFurniInternalValue(room, item, key);
            if (currentValue == null) continue;

            Integer referenceValue = this.referenceFor(references, item.getId(), TARGET_FURNI, index++);
            if (!this.isUnaryOperation() && referenceValue == null) continue;

            this.writeFurniInternalValue(room, item, key, this.applyOperation(currentValue, referenceValue));
        }
    }

    private void executeRoom(WiredContext ctx, Room room) {
        if (isInternalVariableToken(this.destinationVariableToken)) return;

        WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(this.destinationVariableItemId);
        if (definition == null || !definition.hasValue() || definition.isReadOnly()) return;

        ReferenceSnapshot references = this.resolveReferences(ctx, room);
        Integer referenceValue = this.referenceFor(references, room.getId(), TARGET_ROOM, 0);
        if (!this.isUnaryOperation() && referenceValue == null) return;

        int currentValue = room.getRoomVariableManager().getCurrentValue(this.destinationVariableItemId);
        room.getRoomVariableManager().updateVariableValue(this.destinationVariableItemId, this.applyOperation(currentValue, referenceValue));
    }

    private void executeContext(WiredContext ctx, Room room) {
        if (isInternalVariableToken(this.destinationVariableToken)) return;

        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, this.destinationVariableItemId);
        if (definition == null || !definition.hasValue() || definition.isReadOnly()) return;

        ReferenceSnapshot references = this.resolveReferences(ctx, room);
        Integer referenceValue = this.referenceFor(references, this.destinationVariableItemId, TARGET_CONTEXT, 0);
        if (!this.isUnaryOperation() && referenceValue == null) return;
        if (!WiredContextVariableSupport.hasVariable(ctx, this.destinationVariableItemId)) return;

        Integer currentValue = WiredContextVariableSupport.getCurrentValue(ctx, this.destinationVariableItemId);
        int nextValue = this.applyOperation(currentValue != null ? currentValue : 0, referenceValue);
        WiredContextVariableSupport.updateVariableValue(ctx, room, this.destinationVariableItemId, nextValue);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateItems(this.destinationSelectedFurni);
        this.validateItems(this.referenceSelectedFurni);

        List<HabboItem> selectedItems = new ArrayList<>();
        if (this.destinationTargetType == TARGET_FURNI && this.destinationFurniSource == WiredSourceUtil.SOURCE_SELECTED) selectedItems.addAll(this.destinationSelectedFurni);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(selectedItems.size());
        for (HabboItem item : selectedItems) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.serializeStringData());
        message.appendInt(9);
        message.appendInt(this.destinationTargetType);
        message.appendInt(this.operation);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceConstantValue);
        message.appendInt(this.referenceTargetType);
        message.appendInt(this.destinationUserSource);
        message.appendInt(this.destinationFurniSource);
        message.appendInt(this.referenceUserSource);
        message.appendInt(this.referenceFurniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = this.getRoom();
        if (room == null) throw new WiredSaveException("Room not found");

        int[] params = settings.getIntParams();
        String[] stringParts = this.parseStringData(settings.getStringParam());
        int nextDestinationTargetType = normalizeTargetType(param(params, 0, TARGET_USER));
        int nextOperation = normalizeOperation(param(params, 1, OP_ASSIGN));
        int nextReferenceMode = normalizeReferenceMode(param(params, 2, REF_CONSTANT));
        int nextReferenceConstantValue = param(params, 3, 0);
        int nextReferenceTargetType = normalizeTargetType(param(params, 4, TARGET_USER));
        int nextDestinationUserSource = normalizeUserSource(param(params, 5, WiredSourceUtil.SOURCE_TRIGGER));
        int nextDestinationFurniSource = normalizeDestinationFurniSource(param(params, 6, WiredSourceUtil.SOURCE_TRIGGER));
        int nextReferenceUserSource = normalizeUserSource(param(params, 7, WiredSourceUtil.SOURCE_TRIGGER));
        int nextReferenceFurniSource = normalizeReferenceFurniSource(param(params, 8, WiredSourceUtil.SOURCE_TRIGGER));
        String nextDestinationVariableToken = normalizeVariableToken((stringParts.length > 0) ? stringParts[0] : "");
        String nextReferenceVariableToken = normalizeVariableToken((stringParts.length > 1) ? stringParts[1] : "");

        this.validateDestination(room, nextDestinationTargetType, nextDestinationVariableToken);
        if (nextReferenceMode == REF_VARIABLE) this.validateReference(room, nextReferenceTargetType, nextReferenceVariableToken);

        int maxDelay = Emulator.getConfig().getInt("hotel.wired.max_delay", 20);
        if (settings.getDelay() > maxDelay) throw new WiredSaveException("Delay too long");

        List<HabboItem> nextDestinationItems = (nextDestinationTargetType == TARGET_FURNI && nextDestinationFurniSource == WiredSourceUtil.SOURCE_SELECTED) ? this.parseItems(settings.getFurniIds(), room) : new ArrayList<>();
        List<HabboItem> nextReferenceItems = (nextReferenceMode == REF_VARIABLE && nextReferenceTargetType == TARGET_FURNI && nextReferenceFurniSource == SOURCE_SECONDARY_SELECTED) ? this.parseItems((stringParts.length > 2) ? stringParts[2] : "", room) : new ArrayList<>();
        int selectionLimit = Emulator.getConfig().getInt("hotel.wired.furni.selection.count");
        if (nextDestinationItems.size() > selectionLimit || nextReferenceItems.size() > selectionLimit) throw new WiredSaveException("Too many furni selected");

        this.destinationSelectedFurni.clear();
        this.destinationSelectedFurni.addAll(nextDestinationItems);
        this.referenceSelectedFurni.clear();
        this.referenceSelectedFurni.addAll(nextReferenceItems);
        this.destinationTargetType = nextDestinationTargetType;
        this.setDestinationVariableToken(nextDestinationVariableToken);
        this.operation = nextOperation;
        this.referenceMode = nextReferenceMode;
        this.referenceConstantValue = nextReferenceConstantValue;
        this.referenceTargetType = nextReferenceTargetType;
        this.setReferenceVariableToken(nextReferenceVariableToken);
        this.destinationUserSource = nextDestinationUserSource;
        this.destinationFurniSource = nextDestinationFurniSource;
        this.referenceUserSource = nextReferenceUserSource;
        this.referenceFurniSource = nextReferenceFurniSource;
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.destinationTargetType, this.destinationVariableToken, this.destinationVariableItemId, this.operation, this.referenceMode, this.referenceConstantValue, this.referenceTargetType, this.referenceVariableToken, this.referenceVariableItemId, this.destinationUserSource, this.destinationFurniSource, this.referenceUserSource, this.referenceFurniSource, this.getDelay(), this.toIds(this.destinationSelectedFurni), this.toIds(this.referenceSelectedFurni)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) return;

        JsonData data = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.destinationTargetType = normalizeTargetType(data.destinationTargetType);
        this.setDestinationVariableToken(normalizeVariableToken((data.destinationVariableToken != null) ? data.destinationVariableToken : ((data.destinationVariableItemId > 0) ? String.valueOf(data.destinationVariableItemId) : "")));
        this.operation = normalizeOperation(data.operation);
        this.referenceMode = normalizeReferenceMode(data.referenceMode);
        this.referenceConstantValue = data.referenceConstantValue;
        this.referenceTargetType = normalizeTargetType(data.referenceTargetType);
        this.setReferenceVariableToken(normalizeVariableToken((data.referenceVariableToken != null) ? data.referenceVariableToken : ((data.referenceVariableItemId > 0) ? String.valueOf(data.referenceVariableItemId) : "")));
        this.destinationUserSource = normalizeUserSource(data.destinationUserSource);
        this.destinationFurniSource = normalizeDestinationFurniSource(data.destinationFurniSource);
        this.referenceUserSource = normalizeUserSource(data.referenceUserSource);
        this.referenceFurniSource = normalizeReferenceFurniSource(data.referenceFurniSource);
        this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));

        if (room != null) {
            try {
                this.destinationSelectedFurni.addAll(this.parseItems(data.destinationSelectedFurniIds, room));
                this.referenceSelectedFurni.addAll(this.parseItems(data.referenceSelectedFurniIds, room));
            } catch (WiredSaveException ignored) {
            }
        }
    }

    @Override
    public void onPickUp() {
        this.destinationTargetType = TARGET_USER;
        this.setDestinationVariableToken("");
        this.operation = OP_ASSIGN;
        this.referenceMode = REF_CONSTANT;
        this.referenceConstantValue = 0;
        this.referenceTargetType = TARGET_USER;
        this.setReferenceVariableToken("");
        this.destinationUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.destinationFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.destinationSelectedFurni.clear();
        this.referenceSelectedFurni.clear();
        this.setDelay(0);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return (this.destinationTargetType == TARGET_USER && this.destinationUserSource == WiredSourceUtil.SOURCE_TRIGGER)
            || (this.referenceMode == REF_VARIABLE && this.referenceTargetType == TARGET_USER && this.referenceUserSource == WiredSourceUtil.SOURCE_TRIGGER);
    }

    private ReferenceSnapshot resolveReferences(WiredContext ctx, Room room) {
        if (this.referenceMode != REF_VARIABLE) return null;

        return switch (this.referenceTargetType) {
            case TARGET_USER -> this.userReferences(ctx, room);
            case TARGET_FURNI -> this.furniReferences(ctx, room);
            case TARGET_CONTEXT -> this.contextReferences(ctx, room);
            case TARGET_ROOM -> this.roomReferences(room);
            default -> null;
        };
    }

    private ReferenceSnapshot userReferences(WiredContext ctx, Room room) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_USER);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseUserInternalReference(key)) return null;

            for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.referenceUserSource)) {
                if (roomUnit == null) continue;

                Integer value = this.readUserInternalValue(room, roomUnit, key);
                if (value != null) snapshot.add(roomUnit.getId(), value);
            }

            return snapshot.isEmpty() ? null : snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        if (definition == null || !definition.hasValue()) return null;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.referenceUserSource)) {
            if (roomUnit == null) continue;

            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null) snapshot.add(roomUnit.getId(), room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), this.referenceVariableItemId));
        }

        return snapshot.isEmpty() ? null : snapshot;
    }

    private ReferenceSnapshot furniReferences(WiredContext ctx, Room room) {
        int source = (this.referenceFurniSource == SOURCE_SECONDARY_SELECTED) ? WiredSourceUtil.SOURCE_SELECTED : this.referenceFurniSource;
        if (source == WiredSourceUtil.SOURCE_SELECTED) this.validateItems(this.referenceSelectedFurni);

        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_FURNI);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseFurniInternalReference(key)) return null;

            for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, this.referenceSelectedFurni)) {
                if (item == null) continue;

                Integer value = this.readFurniInternalValue(room, item, key);
                if (value != null) snapshot.add(item.getId(), value);
            }

            return snapshot.isEmpty() ? null : snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        if (definition == null || !definition.hasValue()) return null;

        for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, this.referenceSelectedFurni)) {
            if (item != null) snapshot.add(item.getId(), room.getFurniVariableManager().getCurrentValue(item.getId(), this.referenceVariableItemId));
        }

        return snapshot.isEmpty() ? null : snapshot;
    }

    private ReferenceSnapshot roomReferences(Room room) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_ROOM);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseRoomInternalReference(key)) return null;

            Integer value = this.readRoomInternalValue(room, key);
            if (value == null) return null;

            snapshot.add(room.getId(), value);
            return snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(this.referenceVariableItemId);
        if (definition == null || !definition.hasValue()) return null;

        snapshot.add(room.getId(), room.getRoomVariableManager().getCurrentValue(this.referenceVariableItemId));
        return snapshot;
    }

    private ReferenceSnapshot contextReferences(WiredContext ctx, Room room) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_CONTEXT);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseContextInternalReference(key)) return null;

            Integer value = WiredInternalVariableSupport.readContextValue(ctx, key);
            if (value == null) return null;

            snapshot.add(this.referenceVariableItemId > 0 ? this.referenceVariableItemId : room.getId(), value);
            return snapshot;
        }

        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, this.referenceVariableItemId);
        if (definition == null || !definition.hasValue() || !WiredContextVariableSupport.hasVariable(ctx, this.referenceVariableItemId)) return null;

        Integer value = WiredContextVariableSupport.getCurrentValue(ctx, this.referenceVariableItemId);
        if (value == null) return null;

        snapshot.add(this.referenceVariableItemId, value);
        return snapshot;
    }

    private Integer referenceFor(ReferenceSnapshot snapshot, int destinationEntityId, int destinationTarget, int destinationIndex) {
        if (this.referenceMode != REF_VARIABLE) return this.referenceConstantValue;
        if (this.isUnaryOperation()) return 0;
        if (snapshot == null || snapshot.isEmpty()) return null;
        if (snapshot.targetType == destinationTarget && snapshot.values.containsKey(destinationEntityId)) return snapshot.values.get(destinationEntityId);
        if (destinationIndex >= 0 && destinationIndex < snapshot.values.size()) return new ArrayList<>(snapshot.values.values()).get(destinationIndex);
        return new ArrayList<>(snapshot.values.values()).get(0);
    }

    private int applyOperation(int currentValue, Integer referenceValue) {
        return switch (this.operation) {
            case OP_ASSIGN -> (referenceValue != null) ? referenceValue : currentValue;
            case OP_ADD -> clamp((long) currentValue + referenceValue);
            case OP_SUB -> clamp((long) currentValue - referenceValue);
            case OP_MUL -> clamp((long) currentValue * referenceValue);
            case OP_DIV -> (referenceValue == null || referenceValue == 0) ? currentValue : (currentValue / referenceValue);
            case OP_POW -> (referenceValue == null || referenceValue < 0) ? 0 : clamp(Math.round(Math.pow(currentValue, referenceValue)));
            case OP_MOD -> (referenceValue == null || referenceValue == 0) ? currentValue : (currentValue % referenceValue);
            case OP_MIN -> (referenceValue != null) ? Math.min(currentValue, referenceValue) : currentValue;
            case OP_MAX -> (referenceValue != null) ? Math.max(currentValue, referenceValue) : currentValue;
            case OP_RANDOM -> (referenceValue == null || referenceValue <= 0) ? 0 : Emulator.getRandom().nextInt(referenceValue + 1);
            case OP_ABS -> (currentValue == Integer.MIN_VALUE) ? Integer.MAX_VALUE : Math.abs(currentValue);
            case OP_AND -> (referenceValue != null) ? (currentValue & referenceValue) : currentValue;
            case OP_OR -> (referenceValue != null) ? (currentValue | referenceValue) : currentValue;
            case OP_XOR -> (referenceValue != null) ? (currentValue ^ referenceValue) : currentValue;
            case OP_NOT -> ~currentValue;
            case OP_LSHIFT -> currentValue << shift(referenceValue);
            case OP_RSHIFT -> currentValue >> shift(referenceValue);
            default -> currentValue;
        };
    }

    private boolean isUnaryOperation() {
        return this.operation == OP_ABS || this.operation == OP_NOT;
    }

    private void validateDestination(Room room, int targetType, String variableToken) throws WiredSaveException {
        if (variableToken == null || variableToken.isEmpty()) throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");

        boolean valid = switch (targetType) {
            case TARGET_USER -> isInternalVariableToken(variableToken)
                ? canUseUserInternalDestination(getInternalVariableKey(variableToken))
                : this.isValidUserCustomDestination(room, getCustomItemId(variableToken));
            case TARGET_FURNI -> isInternalVariableToken(variableToken)
                ? canUseFurniInternalDestination(getInternalVariableKey(variableToken))
                : this.isValidFurniCustomDestination(room, getCustomItemId(variableToken));
            case TARGET_CONTEXT -> !isInternalVariableToken(variableToken)
                && this.isValidContextCustomDestination(room, getCustomItemId(variableToken));
            case TARGET_ROOM -> !isInternalVariableToken(variableToken) && this.isValidRoomCustomDestination(room, getCustomItemId(variableToken));
            default -> false;
        };

        if (!valid) throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
    }

    private void validateReference(Room room, int targetType, String variableToken) throws WiredSaveException {
        if (variableToken == null || variableToken.isEmpty()) throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");

        boolean valid = switch (targetType) {
            case TARGET_USER -> isInternalVariableToken(variableToken)
                ? canUseUserInternalReference(getInternalVariableKey(variableToken))
                : this.isValidUserCustomReference(room, getCustomItemId(variableToken));
            case TARGET_FURNI -> isInternalVariableToken(variableToken)
                ? canUseFurniInternalReference(getInternalVariableKey(variableToken))
                : this.isValidFurniCustomDestination(room, getCustomItemId(variableToken));
            case TARGET_CONTEXT -> isInternalVariableToken(variableToken)
                ? canUseContextInternalReference(getInternalVariableKey(variableToken))
                : this.isValidContextCustomReference(room, getCustomItemId(variableToken));
            case TARGET_ROOM -> isInternalVariableToken(variableToken)
                ? canUseRoomInternalReference(getInternalVariableKey(variableToken))
                : this.isValidRoomCustomReference(room, getCustomItemId(variableToken));
            default -> false;
        };

        if (!valid) throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
    }

    private boolean isValidUserCustomDestination(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getUserVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue() && !definition.isReadOnly();
    }

    private boolean isValidFurniCustomDestination(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getFurniVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue() && !definition.isReadOnly();
    }

    private boolean isValidRoomCustomDestination(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getRoomVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue() && !definition.isReadOnly();
    }

    private boolean isValidContextCustomDestination(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, variableItemId);
        return definition != null && definition.hasValue() && !definition.isReadOnly();
    }

    private boolean isValidUserCustomReference(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getUserVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue();
    }

    private boolean isValidRoomCustomReference(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getRoomVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue();
    }

    private boolean isValidContextCustomReference(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, variableItemId);
        return definition != null && definition.hasValue();
    }

    private boolean isValidFurniCustomReference(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getFurniVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && definition.hasValue();
    }

    private Integer readUserInternalValue(Room room, RoomUnit roomUnit, String key) {
        return WiredInternalVariableSupport.readUserValue(room, roomUnit, key);
    }

    private boolean writeUserInternalValue(Room room, RoomUnit roomUnit, String key, int value) {
        return WiredInternalVariableSupport.writeUserValue(
            room,
            roomUnit,
            key,
            value,
            WiredUserMovementHelper.DEFAULT_ANIMATION_DURATION,
            false);
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
        if (room == null || habbo == null || habbo.getHabboInfo().getGamePlayer() == null) return null;

        Game game = this.resolveTeamGame(room, habbo);
        GamePlayer gamePlayer = habbo.getHabboInfo().getGamePlayer();

        if (game == null || gamePlayer.getTeamColor() == null) return gamePlayer.getScore();

        GameTeam team = game.getTeam(gamePlayer.getTeamColor());
        return (team != null) ? team.getTotalScore() : gamePlayer.getScore();
    }

    private Integer getTeamColorId(int effectId) {
        TeamEffectData data = this.getTeamEffectData(effectId);
        return data == null ? null : data.colorId;
    }

    private Integer getTeamTypeId(int effectId) {
        TeamEffectData data = this.getTeamEffectData(effectId);
        return data == null ? null : data.typeId;
    }

    private int getTeamMetric(Room room, GameTeamColors color, boolean score) {
        Game game = this.resolveTeamGame(room, null);
        if (game == null || color == null) return 0;

        GameTeam team = game.getTeam(color);
        if (team == null) return 0;

        return score ? team.getTotalScore() : team.getMembers().size();
    }

    private Game resolveTeamGame(Room room, Habbo habbo) {
        if (room == null) return null;

        if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getCurrentGame() != null) {
            Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());
            if (game != null) return game;
        }

        Game wiredGame = room.getGame(WiredGame.class);
        if (wiredGame != null) return wiredGame;

        Game freezeGame = room.getGame(FreezeGame.class);
        if (freezeGame != null) return freezeGame;

        return room.getGame(BattleBanzaiGame.class);
    }

    private TeamEffectData getTeamEffectData(int effectValue) {
        if (effectValue <= 0) return null;

        if (effectValue >= 223 && effectValue <= 226) return new TeamEffectData(effectValue - 222, 0);
        if (effectValue >= 33 && effectValue <= 36) return new TeamEffectData(effectValue - 32, 1);
        if (effectValue >= 40 && effectValue <= 43) return new TeamEffectData(effectValue - 39, 2);

        return null;
    }

    private boolean moveUserTo(Room room, RoomUnit roomUnit, int x, int y) {
        if (room == null || roomUnit == null || room.getLayout() == null) return false;

        RoomTile targetTile = room.getLayout().getTile((short) x, (short) y);
        if (targetTile == null || targetTile.state == RoomTileState.INVALID) return false;

        double targetZ = targetTile.getStackHeight() + ((targetTile.state == RoomTileState.SIT) ? -0.5 : 0);
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

    private List<HabboItem> parseItems(int[] ids, Room room) throws WiredSaveException {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || room == null) return items;

        for (int id : ids) {
            HabboItem item = room.getHabboItem(id);
            if (item == null) throw new WiredSaveException(String.format("Item %s not found", id));
            items.add(item);
        }

        return items;
    }

    private List<HabboItem> parseItems(List<Integer> ids, Room room) throws WiredSaveException {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || room == null) return items;

        for (Integer id : ids) {
            if (id == null || id <= 0) continue;

            HabboItem item = room.getHabboItem(id);
            if (item == null) throw new WiredSaveException(String.format("Item %s not found", id));
            items.add(item);
        }

        return items;
    }

    private List<HabboItem> parseItems(String ids, Room room) throws WiredSaveException {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || ids.trim().isEmpty() || room == null) return items;

        for (String part : ids.split("[;,\\t]")) {
            int id = parseInteger(part);
            if (id <= 0) continue;

            HabboItem item = room.getHabboItem(id);
            if (item == null) throw new WiredSaveException(String.format("Item %s not found", id));
            items.add(item);
        }

        return items;
    }

    private String serializeStringData() {
        return (this.destinationVariableToken == null ? "" : this.destinationVariableToken) + DELIM + (this.referenceVariableToken == null ? "" : this.referenceVariableToken) + DELIM + this.serializeIds(this.referenceSelectedFurni);
    }

    private String[] parseStringData(String value) {
        return (value == null || value.isEmpty()) ? new String[0] : value.split("\\t", -1);
    }

    private List<Integer> toIds(List<HabboItem> items) {
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : items) if (item != null) ids.add(item.getId());
        return ids;
    }

    private String serializeIds(List<HabboItem> items) {
        StringBuilder builder = new StringBuilder();

        for (HabboItem item : items) {
            if (item == null) continue;
            if (builder.length() > 0) builder.append(FURNI_DELIM);
            builder.append(item.getId());
        }

        return builder.toString();
    }

    private void setDestinationVariableToken(String token) {
        this.destinationVariableToken = normalizeVariableToken(token);
        this.destinationVariableItemId = getCustomItemId(this.destinationVariableToken);
    }

    private void setReferenceVariableToken(String token) {
        this.referenceVariableToken = normalizeVariableToken(token);
        this.referenceVariableItemId = getCustomItemId(this.referenceVariableToken);
    }

    private static boolean isCustomVariableToken(String token) {
        return token != null && token.startsWith(CUSTOM_TOKEN_PREFIX);
    }

    private static boolean isInternalVariableToken(String token) {
        return token != null && token.startsWith(INTERNAL_TOKEN_PREFIX);
    }

    private static int getCustomItemId(String token) {
        if (!isCustomVariableToken(token)) return 0;
        return parseInteger(token.substring(CUSTOM_TOKEN_PREFIX.length()));
    }

    private static String getInternalVariableKey(String token) {
        return isInternalVariableToken(token) ? WiredInternalVariableSupport.normalizeKey(token.substring(INTERNAL_TOKEN_PREFIX.length())) : "";
    }

    private static String normalizeVariableToken(String token) {
        if (token == null) return "";

        String normalized = token.trim();
        if (normalized.isEmpty()) return "";
        if (isCustomVariableToken(normalized)) return normalized;
        if (isInternalVariableToken(normalized)) return INTERNAL_TOKEN_PREFIX + WiredInternalVariableSupport.normalizeKey(normalized.substring(INTERNAL_TOKEN_PREFIX.length()));

        int parsedValue = parseInteger(normalized);
        return parsedValue > 0 ? CUSTOM_TOKEN_PREFIX + parsedValue : "";
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

    private static boolean canUseContextInternalReference(String key) {
        return WiredInternalVariableSupport.canUseContextReference(key);
    }

    private static int param(int[] params, int index, int fallback) {
        return (params.length > index) ? params[index] : fallback;
    }

    private static int normalizeTargetType(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    private static int normalizeReferenceMode(int value) {
        return (value == REF_VARIABLE) ? REF_VARIABLE : REF_CONSTANT;
    }

    private static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    private static int normalizeDestinationFurniSource(int value) {
        return switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    private static int normalizeReferenceFurniSource(int value) {
        return switch (value) {
            case SOURCE_SECONDARY_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    private static int normalizeOperation(int value) {
        return switch (value) {
            case OP_ADD, OP_SUB, OP_MUL, OP_DIV, OP_POW, OP_MOD, OP_MIN, OP_MAX, OP_RANDOM, OP_ABS, OP_AND, OP_OR, OP_XOR, OP_NOT, OP_LSHIFT, OP_RSHIFT -> value;
            default -> OP_ASSIGN;
        };
    }

    private static int parseInteger(String value) {
        try {
            return (value == null || value.trim().isEmpty()) ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int shift(Integer value) {
        return (value == null) ? 0 : Math.max(0, Math.min(31, value));
    }

    private static int clamp(long value) {
        return (value > Integer.MAX_VALUE) ? Integer.MAX_VALUE : ((value < Integer.MIN_VALUE) ? Integer.MIN_VALUE : (int) value);
    }

    static class JsonData {
        int destinationTargetType, destinationVariableItemId, operation, referenceMode, referenceConstantValue, referenceTargetType, referenceVariableItemId, destinationUserSource, destinationFurniSource, referenceUserSource, referenceFurniSource, delay;
        String destinationVariableToken, referenceVariableToken;
        List<Integer> destinationSelectedFurniIds, referenceSelectedFurniIds;

        JsonData(int destinationTargetType, String destinationVariableToken, int destinationVariableItemId, int operation, int referenceMode, int referenceConstantValue, int referenceTargetType, String referenceVariableToken, int referenceVariableItemId, int destinationUserSource, int destinationFurniSource, int referenceUserSource, int referenceFurniSource, int delay, List<Integer> destinationSelectedFurniIds, List<Integer> referenceSelectedFurniIds) {
            this.destinationTargetType = destinationTargetType;
            this.destinationVariableToken = destinationVariableToken;
            this.destinationVariableItemId = destinationVariableItemId;
            this.operation = operation;
            this.referenceMode = referenceMode;
            this.referenceConstantValue = referenceConstantValue;
            this.referenceTargetType = referenceTargetType;
            this.referenceVariableToken = referenceVariableToken;
            this.referenceVariableItemId = referenceVariableItemId;
            this.destinationUserSource = destinationUserSource;
            this.destinationFurniSource = destinationFurniSource;
            this.referenceUserSource = referenceUserSource;
            this.referenceFurniSource = referenceFurniSource;
            this.delay = delay;
            this.destinationSelectedFurniIds = destinationSelectedFurniIds;
            this.referenceSelectedFurniIds = referenceSelectedFurniIds;
        }
    }

    private static class ReferenceSnapshot {
        final int targetType;
        final LinkedHashMap<Integer, Integer> values = new LinkedHashMap<>();

        ReferenceSnapshot(int targetType) {
            this.targetType = targetType;
        }

        void add(int entityId, int value) {
            this.values.put(entityId, value);
        }

        boolean isEmpty() {
            return this.values.isEmpty();
        }
    }

    private static class TeamEffectData {
        final int colorId;
        final int typeId;

        TeamEffectData(int colorId, int typeId) {
            this.colorId = colorId;
            this.typeId = typeId;
        }
    }
}
