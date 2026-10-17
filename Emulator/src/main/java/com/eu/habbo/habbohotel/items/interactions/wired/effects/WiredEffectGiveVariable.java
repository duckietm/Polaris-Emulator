package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.rooms.WiredVariableGiveScope;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayView;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.core.WiredVariableDefinitionFlash;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The official "give variable" (AIR code 39). Int params {@code [target, initialHigh, initialLow,
 * override, fromVariable, referenceTarget, sameHolder]}: the target is an official variable target
 * (1 user, 0 furni, -20 context), the initial value a long split in two ints. Since WIN63-202610121624
 * the initial value may come from a variable: read once on the box's selection, or with "same holder"
 * on each target itself. String param: the variable (an id, or {@code internal:<key>}), then a tab and
 * the reference variable's id. Furni slot 0 and user slot 0 are the targets.
 */
public class WiredEffectGiveVariable extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.GIVE_VAR;

    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_CONTEXT = 2;
    /** A global destination: Habbo saves it and the box changes nothing when it runs (5e96a57d). */
    static final int TARGET_GLOBAL = 3;

    private int variableItemId = 0;
    private String internalVariableKey = "";
    private int targetType = TARGET_USER;
    private boolean overrideExisting = false;
    private int initialValue = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean fromVariable = false;
    private int referenceVariableId = 0;
    private int referenceTarget = WiredValueVariable.TARGET_GLOBAL;
    private boolean sameHolder = false;
    private final Set<HabboItem> selectedFurni;

    public WiredEffectGiveVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.selectedFurni = new LinkedHashSet<>();
    }

    public WiredEffectGiveVariable(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.selectedFurni = new LinkedHashSet<>();
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        // A global destination or no variable yet: the box runs (and flashes) but gives nothing.
        if (this.targetType == TARGET_GLOBAL || (this.variableItemId <= 0 && this.internalVariableKey.isEmpty())) {
            return;
        }

        Integer shared = null;
        if (this.fromVariable && !this.sameHolder) {
            shared = WiredValueVariable.read(
                    ctx,
                    room,
                    this.referenceTarget,
                    this.referenceVariableId,
                    this.furniSource,
                    this.selectedFurni,
                    this.userSource);
            if (shared == null) return;
        }

        if (!this.internalVariableKey.isEmpty()) {
            if (this.targetType != TARGET_USER) return;
            for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
                if (unit == null) continue;
                Integer internalValue = this.valueFor(ctx, room, shared, UserVariableHolders.of(room, unit));
                if (internalValue == null) continue;
                if (!this.overrideExisting
                        && WiredInternalVariableSupport.hasUserValue(room, unit, this.internalVariableKey)) continue;
                if ("@has_rights".equals(this.internalVariableKey)) {
                    Habbo habbo = room.getHabbo(unit);
                    if (habbo == null || habbo.getHabboInfo() == null) continue;
                    Integer previous = WiredInternalVariableSupport.readUserValue(room, unit, "@has_rights");
                    room.giveRights(habbo.getHabboInfo().getId());
                    WiredInternalVariableSupport.emitUserChange(
                            room,
                            unit,
                            "@has_rights",
                            previous,
                            WiredInternalVariableSupport.readUserValue(room, unit, "@has_rights"));
                } else {
                    WiredInternalVariableSupport.writeUserValue(room, unit, this.internalVariableKey, internalValue);
                }
            }
            return;
        }

        // A pointer stands for the variable whose WIRED box id its accessor holds in this stack.
        int variableId = WiredPointerVariableSupport.resolve(ctx, room, this.variableItemId);
        if (variableId <= 0) return;

        WiredArrayVariableDefinition arrayDefinition = this.getArrayDefinition(room, variableId);
        if (arrayDefinition != null && arrayDefinition.isArray()) {
            this.giveArray(ctx, room, arrayDefinition);
            return;
        }

        boolean given =
                switch (this.targetType) {
                    case TARGET_USER -> this.executeUserVariables(ctx, room, shared, variableId);
                    case TARGET_FURNI -> this.executeFurniVariables(ctx, room, shared, variableId);
                    case TARGET_CONTEXT -> this.executeContextVariables(ctx, room, shared, variableId);
                    default -> false;
                };
        // A give that gave something also flashes the variable's definition box; one that gave nothing does not.
        if (given) {
            WiredVariableDefinitionFlash.flash(room, variableId);
        }
    }

    /**
     * The initial value for one holder: the box's number, the reference variable read once, or with
     * "same holder" the reference variable on this holder (null when it has none: it is skipped).
     */
    private Integer valueFor(WiredContext ctx, Room room, Integer shared, int holderId) {
        if (!this.fromVariable) return this.initialValue;
        if (!this.sameHolder) return shared;

        int id = WiredPointerVariableSupport.resolve(ctx, room, this.referenceVariableId);
        return switch (this.targetType) {
            case TARGET_FURNI ->
                room.getFurniVariableManager().hasVariable(holderId, id)
                        ? room.getFurniVariableManager().getCurrentValue(holderId, id)
                        : null;
            case TARGET_CONTEXT ->
                WiredContextVariableSupport.hasVariable(ctx, id)
                        ? WiredContextVariableSupport.getCurrentValue(ctx, id)
                        : null;
            default ->
                holderId != 0 && room.getUserVariableManager().hasVariable(holderId, id)
                        ? room.getUserVariableManager().getCurrentValue(holderId, id)
                        : null;
        };
    }

    private boolean executeContextVariables(WiredContext ctx, Room room, Integer shared, int variableId) {
        WiredVariableDefinitionInfo definitionInfo = WiredContextVariableSupport.getDefinitionInfo(room, variableId);
        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        boolean existed = WiredContextVariableSupport.hasVariable(ctx, variableId);
        Integer previous = WiredContextVariableSupport.getCurrentValue(ctx, variableId);
        Integer value = definitionInfo.hasValue() ? this.valueFor(ctx, room, shared, 0) : null;
        if (definitionInfo.hasValue() && value == null) return false;

        if (WiredContextVariableSupport.assignVariable(ctx, room, variableId, value, this.overrideExisting)) {
            long oldValue = previous == null ? 0L : previous;
            long newValue = value == null ? 0L : value;
            // A give that kept the value fires nothing; any other give reports the variable as created.
            if (existed && definitionInfo.hasValue() && newValue == oldValue) return true;
            WiredEvent.VariableChangeKind kind = !existed || !definitionInfo.hasValue()
                    ? WiredEvent.VariableChangeKind.NONE
                    : newValue == oldValue
                            ? WiredEvent.VariableChangeKind.UNCHANGED
                            : newValue > oldValue
                                    ? WiredEvent.VariableChangeKind.INCREASED
                                    : WiredEvent.VariableChangeKind.DECREASED;
            WiredContextVariableSupport.triggerVariableChanged(
                    ctx, room, variableId, true, false, kind, oldValue, newValue);
            return true;
        }
        return false;
    }

    private void giveArray(WiredContext ctx, Room room, WiredArrayVariableDefinition definition) {
        List<WiredArrayRuntimeSupport.Owner> owners = WiredArrayRuntimeSupport.resolveOwners(
                ctx,
                this.selectedFurni,
                definition,
                this.targetType == TARGET_FURNI ? this.furniSource : this.userSource);

        if (!WiredArrayRuntimeSupport.allowAssignmentWork(ctx, definition, owners)) {
            return;
        }

        for (WiredArrayRuntimeSupport.Owner owner : owners) {
            boolean changed;
            WiredArrayView previous;
            if (definition.getArrayVariableType() == WiredArrayVariableType.CONTEXT) {
                previous = ctx.contextVariables().getArrayView(definition.getId(), definition.getArrayDefinition());
                changed = ctx.contextVariables()
                                .giveArray(definition.getId(), definition.getArrayDefinition(), this.overrideExisting)
                        == WiredArrayMutationResult.SUCCESS;
            } else {
                var outcome = room.getArrayVariableManager().give(definition, owner.id(), this.overrideExisting);
                changed = outcome.changed();
                previous = outcome.previousValue();
            }
            if (changed)
                WiredArrayRuntimeSupport.dispatchChange(
                        ctx,
                        definition,
                        owner,
                        previous == null
                                ? WiredArrayChange.created()
                                : WiredArrayChange.structural(
                                        WiredArrayStructuralOperation.CLEAR,
                                        0,
                                        0,
                                        previous.getLengthForCondition(),
                                        0));
        }
    }

    private WiredArrayVariableDefinition getArrayDefinition(Room room, int variableId) {
        int variableType =
                switch (this.targetType) {
                    case TARGET_FURNI -> WiredArrayVariableType.FURNI.code();
                    case TARGET_CONTEXT -> WiredArrayVariableType.CONTEXT.code();
                    default -> WiredArrayVariableType.USER.code();
                };
        return WiredArrayDefinitionSupport.resolve(room, variableType, variableId);
    }

    private boolean executeUserVariables(WiredContext ctx, Room room, Integer shared, int variableId) {
        WiredVariableDefinitionInfo definitionInfo =
                room.getUserVariableManager().getDefinitionInfo(variableId);

        if (definitionInfo == null || definitionInfo.isReadOnly()) {
            return false;
        }

        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        boolean given = false;

        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) {
                continue;
            }

            int holder = UserVariableHolders.of(room, roomUnit);

            if (holder == 0) {
                continue;
            }

            Integer value = definitionInfo.hasValue() ? this.valueFor(ctx, room, shared, holder) : null;
            if (definitionInfo.hasValue() && value == null) {
                continue;
            }

            // A give always reports the value as created (see WiredVariableGiveScope).
            given |= WiredVariableGiveScope.give(() ->
                    room.getUserVariableManager().assignVariable(holder, variableId, value, this.overrideExisting));
        }
        return given;
    }

    private boolean executeFurniVariables(WiredContext ctx, Room room, Integer shared, int variableId) {
        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(variableId);

        if (definition == null || definition.isReadOnly()) {
            return false;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.validateItems(this.selectedFurni);
        }

        List<HabboItem> furni = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedFurni);
        boolean given = false;

        for (HabboItem item : furni) {
            if (item == null) {
                continue;
            }

            Integer value = definition.hasValue() ? this.valueFor(ctx, room, shared, item.getId()) : null;
            if (definition.hasValue() && value == null) {
                continue;
            }

            given |= WiredVariableGiveScope.give(() ->
                    room.getFurniVariableManager().assignVariable(item, variableId, value, this.overrideExisting));
        }
        return given;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> selectedItems = new ArrayList<>();

        if (this.targetType == TARGET_FURNI && this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (HabboItem item : this.selectedFurni) {
                if (item != null && room != null && room.getHabboItem(item.getId()) != null) {
                    selectedItems.add(item);
                }
            }
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(selectedItems.size());

        for (HabboItem item : selectedItems) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString((this.internalVariableKey.isEmpty()
                        ? String.valueOf(this.variableItemId)
                        : "internal:" + this.internalVariableKey)
                + "\t"
                + this.referenceVariableId);
        message.appendInt(7);
        message.appendInt(
                this.targetType == TARGET_GLOBAL
                        ? WiredValueVariable.TARGET_GLOBAL
                        : WiredValueVariable.officialOf(this.targetType));
        message.appendInt(this.initialValue < 0 ? -1 : 0);
        message.appendInt(this.initialValue);
        message.appendInt(this.overrideExisting ? 1 : 0);
        message.appendInt(this.fromVariable ? 1 : 0);
        message.appendInt(this.referenceTarget);
        message.appendInt(this.sameHolder ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        int[] intParams = settings.getIntParams();
        int holder =
                WiredValueVariable.holderOf((intParams.length > 0) ? intParams[0] : WiredValueVariable.TARGET_USER);
        boolean globalTarget = intParams.length > 0 && intParams[0] == WiredValueVariable.TARGET_GLOBAL;
        int nextTargetType = globalTarget ? TARGET_GLOBAL : normalizeTargetType(holder < 0 ? TARGET_USER : holder);
        long initialLong = (intParams.length > 2) ? (((long) intParams[1]) << 32) | (intParams[2] & 0xFFFFFFFFL) : 0L;
        int nextInitialValue = (int) Math.clamp(initialLong, Integer.MIN_VALUE, Integer.MAX_VALUE);
        boolean nextOverrideExisting = (intParams.length > 3) && (intParams[3] == 1);
        boolean nextFromVariable = (intParams.length > 4) && (intParams[4] == 1);
        int nextReferenceTarget = WiredValueVariable.normalizeTarget(
                (intParams.length > 5) ? intParams[5] : WiredValueVariable.TARGET_GLOBAL);
        boolean nextSameHolder = nextFromVariable && (intParams.length > 6) && (intParams[6] == 1);
        int[] userSources = settings.getUserSourceTypes();
        int[] furniSources = settings.getFurniSourceTypes();
        int nextUserSource =
                normalizeUserSource(userSources.length > 0 ? userSources[0] : WiredSourceUtil.SOURCE_TRIGGER);
        int nextFurniSource =
                normalizeFurniSource(furniSources.length > 0 ? furniSources[0] : WiredSourceUtil.SOURCE_TRIGGER);

        String text = settings.getStringParam() == null ? "" : settings.getStringParam();
        int tab = text.indexOf('\t');
        String variableText = tab < 0 ? text : text.substring(0, tab);
        int nextReferenceVariableId = tab < 0 ? 0 : WiredValueVariable.parseId(text.substring(tab + 1));
        if (nextFromVariable && nextReferenceVariableId <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }
        int nextVariableItemId = parseVariableItemId(variableText);
        String nextInternalKey = normalizeInternalKey(variableText);
        boolean internal = variableText.trim().startsWith("internal:");
        if (internal && (nextInternalKey.isEmpty() || nextTargetType != TARGET_USER)) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        if (!internal && nextTargetType != TARGET_GLOBAL) {

            if (nextVariableItemId <= 0 && settings.getFurniIds() != null && settings.getFurniIds().length > 0) {
                int legacyItemId = settings.getFurniIds()[0];

                if (room.getUserVariableManager().hasDefinition(legacyItemId)) {
                    nextVariableItemId = legacyItemId;
                    nextTargetType = TARGET_USER;
                }
            }

            // No variable chosen yet: Habbo saves the box, and it does nothing until one is (5a3c7a05).
            boolean chosen = nextVariableItemId > 0;

            WiredVariableDefinitionInfo userDefinitionInfo = (chosen && nextTargetType == TARGET_USER)
                    ? room.getUserVariableManager().getDefinitionInfo(nextVariableItemId)
                    : null;

            if (chosen
                    && nextTargetType == TARGET_USER
                    && (userDefinitionInfo == null || userDefinitionInfo.isReadOnly())) {
                throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
            }

            if (chosen && nextTargetType == TARGET_FURNI) {
                WiredVariableDefinitionInfo furniDefinitionInfo =
                        room.getFurniVariableManager().getDefinitionInfo(nextVariableItemId);

                if (furniDefinitionInfo == null || furniDefinitionInfo.isReadOnly()) {
                    throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                }
            }

            if (chosen && nextTargetType == TARGET_CONTEXT) {
                WiredVariableDefinitionInfo contextDefinitionInfo =
                        WiredContextVariableSupport.getDefinitionInfo(room, nextVariableItemId);

                if (contextDefinitionInfo == null || contextDefinitionInfo.isReadOnly()) {
                    throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                }
            }
        }

        this.selectedFurni.clear();

        if (nextTargetType == TARGET_FURNI && nextFurniSource == WiredSourceUtil.SOURCE_SELECTED) {
            int[] furniIds = settings.getFurniIds();
            int itemsCount = (furniIds != null) ? furniIds.length : 0;

            if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
                throw new WiredSaveException("Too many furni selected");
            }

            for (int i = 0; i < itemsCount; i++) {
                int itemId = furniIds[i];
                HabboItem item = room.getHabboItem(itemId);

                if (item == null) {
                    throw new WiredSaveException(String.format("Item %s not found", itemId));
                }

                this.selectedFurni.add(item);
            }
        }

        this.variableItemId = internal ? 0 : nextVariableItemId;
        this.internalVariableKey = nextInternalKey;
        this.targetType = nextTargetType;
        this.overrideExisting = nextOverrideExisting;
        this.initialValue = nextInitialValue;
        this.userSource = nextUserSource;
        this.furniSource = nextFurniSource;
        this.fromVariable = nextFromVariable;
        this.referenceVariableId = nextFromVariable ? nextReferenceVariableId : 0;
        this.referenceTarget = nextReferenceTarget;
        this.sameHolder = nextSameHolder;
        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public String getWiredData() {
        List<Integer> selectedItemIds = new ArrayList<>();

        for (HabboItem item : this.selectedFurni) {
            if (item != null) {
                selectedItemIds.add(item.getId());
            }
        }

        JsonData data = new JsonData(
                this.variableItemId,
                this.targetType,
                this.overrideExisting,
                this.initialValue,
                this.userSource,
                this.furniSource,
                this.getDelay(),
                selectedItemIds);
        data.internalVariableKey = this.internalVariableKey.isEmpty() ? null : this.internalVariableKey;
        data.fromVariable = this.fromVariable;
        data.referenceVariableId = this.referenceVariableId;
        data.referenceTarget = this.referenceTarget;
        data.sameHolder = this.sameHolder;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.variableItemId = Math.max(0, data.variableItemId);
                this.targetType = normalizeTargetType(data.targetType);
                this.internalVariableKey = this.targetType == TARGET_USER
                        ? normalizeInternalKey("internal:" + data.internalVariableKey)
                        : "";
                if (!this.internalVariableKey.isEmpty()) this.variableItemId = 0;
                this.overrideExisting = data.overrideExisting;
                this.initialValue = data.initialValue;
                this.userSource = normalizeUserSource(data.userSource);
                this.furniSource = normalizeFurniSource(data.furniSource);
                this.referenceVariableId = Math.max(0, data.referenceVariableId);
                this.fromVariable = data.fromVariable && this.referenceVariableId > 0;
                this.referenceTarget = data.referenceTarget == null
                        ? WiredValueVariable.TARGET_GLOBAL
                        : WiredValueVariable.normalizeTarget(data.referenceTarget);
                this.sameHolder = this.fromVariable && data.sameHolder;
                this.setDelay(WiredInputGuard.normalizeDelay(data.delay));

                if (room != null && data.selectedFurniIds != null) {
                    for (Integer itemId : data.selectedFurniIds) {
                        if (itemId == null || itemId <= 0) {
                            continue;
                        }

                        HabboItem item = room.getHabboItem(itemId);

                        if (item != null) {
                            this.selectedFurni.add(item);
                        }
                    }
                }
            }

            return;
        }

        try {
            this.variableItemId = Math.max(0, Integer.parseInt(wiredData.trim()));
            this.targetType = TARGET_USER;
        } catch (NumberFormatException ignored) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EFFECT_GIVE_VARIABLE_LEGACY,
                    this.getRoomId(),
                    this.getId(),
                    ignored);
        }
    }

    @Override
    public void onPickUp() {
        this.variableItemId = 0;
        this.internalVariableKey = "";
        this.targetType = TARGET_USER;
        this.overrideExisting = false;
        this.initialValue = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.fromVariable = false;
        this.referenceVariableId = 0;
        this.referenceTarget = WiredValueVariable.TARGET_GLOBAL;
        this.sameHolder = false;
        this.selectedFurni.clear();
        this.setDelay(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource};
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public WiredEffectType getType() {
        return type;
    }

    public int getVariableItemId() {
        return this.variableItemId;
    }

    public int getTargetType() {
        return this.targetType;
    }

    public boolean isOverrideExisting() {
        return this.overrideExisting;
    }

    public int getInitialValue() {
        return this.initialValue;
    }

    public int getUserSource() {
        return this.userSource;
    }

    public int getFurniSource() {
        return this.furniSource;
    }

    public Set<HabboItem> getSelectedFurni() {
        return this.selectedFurni;
    }

    private static int normalizeTargetType(int value) {
        switch (value) {
            case TARGET_FURNI:
            case TARGET_CONTEXT:
            case TARGET_GLOBAL:
                return value;
            default:
                return TARGET_USER;
        }
    }

    private static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    private static int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    private static String normalizeInternalKey(String token) {
        if (token == null || !token.trim().startsWith("internal:")) return "";
        String key = WiredInternalVariableSupport.normalizeKey(token.trim().substring("internal:".length()));
        return Set.of("@handitem_id", "@effect_id", "@has_rights").contains(key) ? key : "";
    }

    private static int parseVariableItemId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static class JsonData {
        String internalVariableKey;
        boolean fromVariable;
        int referenceVariableId;
        Integer referenceTarget;
        boolean sameHolder;
        int variableItemId;
        int targetType;
        boolean overrideExisting;
        int initialValue;
        int userSource;
        int furniSource;
        int delay;
        List<Integer> selectedFurniIds;

        JsonData(
                int variableItemId,
                int targetType,
                boolean overrideExisting,
                int initialValue,
                int userSource,
                int furniSource,
                int delay,
                List<Integer> selectedFurniIds) {
            this.variableItemId = variableItemId;
            this.targetType = targetType;
            this.overrideExisting = overrideExisting;
            this.initialValue = initialValue;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.delay = delay;
            this.selectedFurniIds = selectedFurniIds;
        }
    }
}
