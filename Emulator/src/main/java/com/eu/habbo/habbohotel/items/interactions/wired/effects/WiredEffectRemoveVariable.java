package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectRemoveVariable extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.REMOVE_VAR;

    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_CONTEXT = 2;

    private int variableItemId = 0;
    private int targetType = TARGET_USER;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private final THashSet<HabboItem> selectedFurni;

    public WiredEffectRemoveVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.selectedFurni = new THashSet<>();
    }

    public WiredEffectRemoveVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.selectedFurni = new THashSet<>();
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        switch (this.targetType) {
            case TARGET_USER:
                this.executeUserVariables(ctx, room);
                return;
            case TARGET_FURNI:
                this.executeFurniVariables(ctx, room);
                return;
            case TARGET_CONTEXT:
                this.executeContextVariables(ctx, room);
                return;
            default:
                return;
        }
    }

    private void executeUserVariables(WiredContext ctx, Room room) {
        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(this.variableItemId);

        if (definition == null || definition.isReadOnly()) {
            return;
        }

        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, this.userSource);

        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) {
                continue;
            }

            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo == null) {
                continue;
            }

            room.getUserVariableManager().removeVariable(habbo.getHabboInfo().getId(), this.variableItemId);
        }
    }

    private void executeFurniVariables(WiredContext ctx, Room room) {
        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(this.variableItemId);

        if (definition == null || definition.isReadOnly()) {
            return;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.validateItems(this.selectedFurni);
        }

        List<HabboItem> furni = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedFurni);

        for (HabboItem item : furni) {
            if (item == null) {
                continue;
            }

            room.getFurniVariableManager().removeVariable(item.getId(), this.variableItemId);
        }
    }

    private void executeContextVariables(WiredContext ctx, Room room) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, this.variableItemId);

        if (definition == null || definition.isReadOnly()) {
            return;
        }

        WiredContextVariableSupport.removeVariable(ctx, room, this.variableItemId);
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
        message.appendString(String.valueOf(this.variableItemId));
        message.appendInt(3);
        message.appendInt(this.targetType);
        message.appendInt(this.userSource);
        message.appendInt(this.furniSource);
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
        int nextTargetType = normalizeTargetType((intParams.length > 0) ? intParams[0] : TARGET_USER);
        int nextUserSource = normalizeUserSource((intParams.length > 1) ? intParams[1] : WiredSourceUtil.SOURCE_TRIGGER);
        int nextFurniSource = normalizeFurniSource((intParams.length > 2) ? intParams[2] : WiredSourceUtil.SOURCE_TRIGGER);
        int nextVariableItemId = parseVariableItemId(settings.getStringParam());

        if (nextVariableItemId <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        switch (nextTargetType) {
            case TARGET_USER:
                WiredVariableDefinitionInfo userDefinition = room.getUserVariableManager().getDefinitionInfo(nextVariableItemId);
                if (userDefinition == null || userDefinition.isReadOnly()) {
                    throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                }
                break;
            case TARGET_FURNI:
                WiredVariableDefinitionInfo furniDefinition = room.getFurniVariableManager().getDefinitionInfo(nextVariableItemId);
                if (furniDefinition == null || furniDefinition.isReadOnly()) {
                    throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                }
                break;
            case TARGET_CONTEXT:
                WiredVariableDefinitionInfo contextDefinition = WiredContextVariableSupport.getDefinitionInfo(room, nextVariableItemId);
                if (contextDefinition == null || contextDefinition.isReadOnly()) {
                    throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
                }
                break;
            default:
                throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
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

        this.variableItemId = nextVariableItemId;
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

        return WiredManager.getGson().toJson(new JsonData(this.variableItemId, this.targetType, this.userSource, this.furniSource, this.getDelay(), selectedItemIds));
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
        }
    }

    @Override
    public void onPickUp() {
        this.variableItemId = 0;
        this.targetType = TARGET_USER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.selectedFurni.clear();
        this.setDelay(0);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    private static int normalizeTargetType(int value) {
        switch (value) {
            case TARGET_FURNI:
            case TARGET_CONTEXT:
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
        int variableItemId;
        int targetType;
        int userSource;
        int furniSource;
        int delay;
        List<Integer> selectedFurniIds;

        JsonData(int variableItemId, int targetType, int userSource, int furniSource, int delay, List<Integer> selectedFurniIds) {
            this.variableItemId = variableItemId;
            this.targetType = targetType;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.delay = delay;
            this.selectedFurniIds = selectedFurniIds;
        }
    }
}
