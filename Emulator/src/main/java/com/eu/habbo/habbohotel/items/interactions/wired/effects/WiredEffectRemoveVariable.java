package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
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
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The official "remove variable" (AIR code 40). Int params {@code [target]}, an official variable
 * target (1 user, 0 furni, -20 context); variable ids {@code [variable]}, no string (older clients sent the variable,
 * an id or {@code internal:<key>}, as the string; still read); furni slot 0 and user slot 0 are the holders it is
 * removed from.
 */
public class WiredEffectRemoveVariable extends InteractionWiredEffect
        implements WiredOfficialSources, WiredOfficialVariableIds {
    public static final WiredEffectType type = WiredEffectType.REMOVE_VAR;

    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_CONTEXT = 2;

    /** A global variable: Habbo saves it and the box leaves the global's value when it runs. */
    static final int TARGET_GLOBAL = 3;

    private int variableItemId = 0;
    /** A presence flag Habbo lets this box give to and remove from floor furni, for the room's lifetime. */
    private static final String FREE_MOVE_KEY = "@can_move_freely";

    private String internalVariableKey = "";
    private int targetType = TARGET_USER;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private final Set<HabboItem> selectedFurni;

    public WiredEffectRemoveVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.selectedFurni = new LinkedHashSet<>();
    }

    public WiredEffectRemoveVariable(
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

        // A global always exists: removing one changes nothing.
        if (this.targetType == TARGET_GLOBAL) {
            return;
        }

        if (FREE_MOVE_KEY.equals(this.internalVariableKey)) {
            if (this.targetType == TARGET_FURNI && room.getWiredRuntime() != null) {
                for (HabboItem item : WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedFurni)) {
                    room.getWiredRuntime().setMovesFreely(item, false);
                }
            }
            return;
        }

        if (!this.internalVariableKey.isEmpty()) {
            if (this.targetType != TARGET_USER) return;
            for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
                if (unit == null) continue;
                if ("@handitem_id".equals(this.internalVariableKey)) {
                    // Drops what the avatar carries; an empty hand has no @handitem to remove.
                    if (unit.getHandItem() > 0) {
                        WiredInternalVariableSupport.writeUserValue(room, unit, "@handitem_id", 0);
                    }
                    continue;
                }
                if ("@habbicon".equals(this.internalVariableKey)) {
                    // Takes the shown habbicon away; a user showing none has no @habbicon to remove.
                    if (WiredInternalVariableSupport.hasUserValue(room, unit, "@habbicon")) {
                        WiredInternalVariableSupport.writeUserValue(room, unit, "@habbicon", 0);
                    }
                    continue;
                }
                if ("@has_rights".equals(this.internalVariableKey)) {
                    Habbo habbo = room.getHabbo(unit);
                    if (habbo == null || habbo.getHabboInfo() == null) continue;
                    Integer previous = WiredInternalVariableSupport.readUserValue(room, unit, "@has_rights");
                    room.removeRights(habbo.getHabboInfo().getId());
                    WiredInternalVariableSupport.emitUserChange(
                            room,
                            unit,
                            "@has_rights",
                            previous,
                            WiredInternalVariableSupport.readUserValue(room, unit, "@has_rights"));
                }
            }
            return;
        }

        // A pointer stands for the variable whose WIRED box id its accessor holds in this stack.
        int variableId = WiredPointerVariableSupport.resolve(ctx, room, this.variableItemId);
        if (variableId <= 0) return;

        WiredArrayVariableDefinition arrayDefinition = this.getArrayDefinition(room, variableId);
        if (arrayDefinition != null && arrayDefinition.isArray()) {
            this.removeArray(ctx, room, arrayDefinition);
            return;
        }

        boolean removed =
                switch (this.targetType) {
                    case TARGET_USER -> this.executeUserVariables(ctx, room, variableId);
                    case TARGET_FURNI -> this.executeFurniVariables(ctx, room, variableId);
                    case TARGET_CONTEXT -> this.executeContextVariables(ctx, room, variableId);
                    default -> false;
                };
        // A remove that removed something shows the remove state on the variable's definition box.
        if (removed) {
            WiredVariableDefinitionFlash.flash(room, variableId, WiredVariableDefinitionFlash.KIND_REMOVED);
        }
    }

    private boolean executeUserVariables(WiredContext ctx, Room room, int variableId) {
        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(variableId);

        if (definition == null || definition.isReadOnly()) {
            return false;
        }

        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        boolean removed = false;

        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) {
                continue;
            }

            int holder = UserVariableHolders.of(room, roomUnit);

            if (holder == 0) {
                continue;
            }

            removed |= room.getUserVariableManager().removeVariable(holder, variableId);
        }
        return removed;
    }

    private void removeArray(WiredContext ctx, Room room, WiredArrayVariableDefinition definition) {
        List<WiredArrayRuntimeSupport.Owner> owners = WiredArrayRuntimeSupport.resolveOwners(
                ctx,
                this.selectedFurni,
                definition,
                this.targetType == TARGET_FURNI ? this.furniSource : this.userSource);

        if (!WiredArrayRuntimeSupport.allowAssignmentWork(ctx, definition, owners)) {
            return;
        }

        for (WiredArrayRuntimeSupport.Owner owner : owners) {
            if (definition.getArrayVariableType() == WiredArrayVariableType.CONTEXT) {
                ctx.contextVariables().removeArray(definition.getId());
            } else {
                room.getArrayVariableManager().remove(definition, owner.id());
            }
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

    private boolean executeFurniVariables(WiredContext ctx, Room room, int variableId) {
        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(variableId);

        if (definition == null || definition.isReadOnly()) {
            return false;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.validateItems(this.selectedFurni);
        }

        List<HabboItem> furni = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedFurni);
        boolean removed = false;

        for (HabboItem item : furni) {
            if (item == null) {
                continue;
            }

            removed |= room.getFurniVariableManager().removeVariable(item.getId(), variableId);
        }
        return removed;
    }

    private boolean executeContextVariables(WiredContext ctx, Room room, int variableId) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, variableId);

        if (definition == null || definition.isReadOnly()) {
            return false;
        }

        Integer previous = WiredContextVariableSupport.getCurrentValue(ctx, variableId);

        if (WiredContextVariableSupport.removeVariable(ctx, room, variableId)) {
            WiredContextVariableSupport.triggerVariableChanged(
                    ctx,
                    room,
                    variableId,
                    false,
                    true,
                    WiredEvent.VariableChangeKind.NONE,
                    previous == null ? 0L : previous,
                    0L);
            return true;
        }
        return false;
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
        message.appendString("");
        message.appendInt(1);
        message.appendInt(
                this.targetType == TARGET_GLOBAL
                        ? WiredValueVariable.TARGET_GLOBAL
                        : WiredValueVariable.officialOf(this.targetType));
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
        int[] userSources = settings.getUserSourceTypes();
        int[] furniSources = settings.getFurniSourceTypes();
        int nextUserSource =
                normalizeUserSource(userSources.length > 0 ? userSources[0] : WiredSourceUtil.SOURCE_TRIGGER);
        int nextFurniSource =
                normalizeFurniSource(furniSources.length > 0 ? furniSources[0] : WiredSourceUtil.SOURCE_TRIGGER);
        String variableText = settings.getStringParam();
        if (WiredVariableIds.present(settings.getVariableIds())) {
            // AIR: the variable is the first variable id; older clients sent it as the string.
            String token = WiredVariableIds.tokenAt(settings.getVariableIds(), 0);
            variableText = token.startsWith(WiredVariableIds.CUSTOM_TOKEN_PREFIX)
                    ? token.substring(WiredVariableIds.CUSTOM_TOKEN_PREFIX.length())
                    : token;
        }
        int nextVariableItemId = parseVariableItemId(variableText);
        String nextInternalKey = normalizeInternalKey(variableText);
        boolean internal = variableText != null && variableText.trim().startsWith("internal:");
        if (internal && (nextInternalKey.isEmpty() || nextTargetType != internalTargetOf(nextInternalKey))) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        if (!internal) {

            if (nextVariableItemId <= 0) {
                throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
            }

            switch (nextTargetType) {
                case TARGET_USER:
                    WiredVariableDefinitionInfo userDefinition =
                            room.getUserVariableManager().getDefinitionInfo(nextVariableItemId);
                    if (userDefinition == null || userDefinition.isReadOnly()) {
                        throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                    }
                    break;
                case TARGET_FURNI:
                    WiredVariableDefinitionInfo furniDefinition =
                            room.getFurniVariableManager().getDefinitionInfo(nextVariableItemId);
                    if (furniDefinition == null || furniDefinition.isReadOnly()) {
                        throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                    }
                    break;
                case TARGET_GLOBAL:
                    // Saved like Give Variable on a global: the box then removes nothing.
                    break;
                case TARGET_CONTEXT:
                    WiredVariableDefinitionInfo contextDefinition =
                            WiredContextVariableSupport.getDefinitionInfo(room, nextVariableItemId);
                    if (contextDefinition == null || contextDefinition.isReadOnly()) {
                        throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                    }
                    break;
                default:
                    throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
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
        this.userSource = nextUserSource;
        this.furniSource = nextFurniSource;
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
                this.userSource,
                this.furniSource,
                this.getDelay(),
                selectedItemIds);
        data.internalVariableKey = this.internalVariableKey.isEmpty() ? null : this.internalVariableKey;
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
            JsonData data = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.variableItemId = Math.max(0, data.variableItemId);
                this.targetType = normalizeTargetType(data.targetType);
                String loadedKey = normalizeInternalKey("internal:" + data.internalVariableKey);
                this.internalVariableKey = this.targetType == internalTargetOf(loadedKey) ? loadedKey : "";
                if (!this.internalVariableKey.isEmpty()) this.variableItemId = 0;
                this.userSource = normalizeUserSource(data.userSource);
                this.furniSource = normalizeFurniSource(data.furniSource);
                this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));

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
                    WiredCompatibilityDiagnostics.FailurePoint.EFFECT_REMOVE_VARIABLE_LEGACY,
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
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.selectedFurni.clear();
        this.setDelay(0);
    }

    @Override
    public String[] officialVariableIds() {
        String token = this.internalVariableKey.isEmpty()
                ? (this.variableItemId > 0 ? WiredVariableIds.CUSTOM_TOKEN_PREFIX + this.variableItemId : "")
                : WiredVariableIds.INTERNAL_TOKEN_PREFIX + this.internalVariableKey;
        return WiredVariableIds.list(WiredVariableIds.toWireForHolder(this.targetType, token));
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
        return Set.of("@has_rights", "@handitem_id", "@habbicon", FREE_MOVE_KEY).contains(key) ? key : "";
    }

    /** The holder an internal variable of this box lives on: {@code @can_move_freely} on floor furni, the rest on users. */
    private static int internalTargetOf(String key) {
        return FREE_MOVE_KEY.equals(key) ? TARGET_FURNI : TARGET_USER;
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
        int variableItemId;
        int targetType;
        int userSource;
        int furniSource;
        int delay;
        List<Integer> selectedFurniIds;

        JsonData(
                int variableItemId,
                int targetType,
                int userSource,
                int furniSource,
                int delay,
                List<Integer> selectedFurniIds) {
            this.variableItemId = variableItemId;
            this.targetType = targetType;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.delay = delay;
            this.selectedFurniIds = selectedFurniIds;
        }
    }
}
