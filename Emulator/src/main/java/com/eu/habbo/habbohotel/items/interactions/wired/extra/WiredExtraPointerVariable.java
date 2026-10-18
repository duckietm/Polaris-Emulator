package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The official pointer variable ({@code wf_var_pointer}, AIR {@code PointerVariable}, variable code 9).
 * A pointer is another variable in disguise: while a stack runs, its accessor context variable holds
 * the furni id of the WIRED box of the variable it stands for, and every box that uses the pointer
 * works on that variable instead (see {@link WiredPointerVariableSupport#resolve}). Outside a running
 * stack it is nothing, so the official client never offers it where there is no execution context
 * ({@code requiresExecutionContext}).
 *
 * <p>Editor layout as in AIR: int params {@code [sourceType]} (the official variable target the pointer
 * is listed under: 0 furni, 1 user, -10 global, -20 context), string param the pointer's name and
 * variable ids {@code [accessor]}, a user-defined context variable with a value.
 */
public class WiredExtraPointerVariable extends InteractionWiredExtra implements WiredOfficialVariableIds {
    /** Our legacy code; the variable definition packet carries the official 9 (see WiredAddonCodes). */
    public static final int CODE = 2009;

    private String variableName = "";
    private int sourceType = WiredVariableTargets.OFFICIAL_FURNI;
    private int accessorVariableId = 0;

    public WiredExtraPointerVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraPointerVariable(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = settings.getRoom();
        if (room == null) {
            room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        }
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        int[] intParams = settings.getIntParams();
        int nextSourceType = normalizeSourceType(intParams.length > 0 ? intParams[0] : 0);
        String normalizedName = WiredVariableNameValidator.normalizeForSave(settings.getStringParam());
        WiredVariableNameValidator.validateDefinitionName(room, this.getId(), normalizedName);

        String[] variableIds = settings.getVariableIds();
        int nextAccessor = parseVariableId(variableIds.length > 0 ? variableIds[0] : "");
        if (nextAccessor <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }
        if (!WiredPointerVariableSupport.isValidAccessor(room, nextAccessor)) {
            throw new WiredSaveException(
                    WiredPointerVariableSupport.isPointer(room, nextAccessor)
                            ? WiredPointerVariableSupport.REQUIRES_EXECUTION_CONTEXT_ERROR
                            : "wiredfurni.params.variables.validation.invalid_variable");
        }

        this.variableName = normalizedName;
        this.sourceType = nextSourceType;
        this.accessorVariableId = nextAccessor;
        WiredContextVariableSupport.broadcastDefinitions(room);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableName, this.sourceType, this.accessorVariableId));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableName);
        message.appendInt(1);
        message.appendInt(this.sourceType);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public String[] officialVariableIds() {
        return this.accessorVariableId > 0 ? new String[] {String.valueOf(this.accessorVariableId)} : new String[0];
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;

        this.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
        this.sourceType = normalizeSourceType(data.sourceType);
        this.accessorVariableId = Math.max(0, data.accessorVariableId);
    }

    @Override
    public void onPickUp() {
        this.variableName = "";
        this.sourceType = WiredVariableTargets.OFFICIAL_FURNI;
        this.accessorVariableId = 0;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getVariableName() {
        return this.variableName;
    }

    /** The official variable target the pointer is listed under (0 furni, 1 user, -10 global, -20 context). */
    public int getSourceType() {
        return this.sourceType;
    }

    /** The context variable holding the furni id of the pointed variable's WIRED box. */
    public int getAccessorVariableId() {
        return this.accessorVariableId;
    }

    /** The four targets AIR's header selector offers; anything else is furni, its default. */
    public static int normalizeSourceType(int value) {
        return switch (value) {
            case WiredVariableTargets.OFFICIAL_USER,
                    WiredVariableTargets.OFFICIAL_GLOBAL,
                    WiredVariableTargets.OFFICIAL_CONTEXT -> value;
            default -> WiredVariableTargets.OFFICIAL_FURNI;
        };
    }

    /** A variable id as the editor sends it: a bare id or our {@code custom:<id>} token. */
    static int parseVariableId(String text) {
        if (text == null) return 0;
        String value = text.trim();
        if (value.startsWith("custom:")) value = value.substring("custom:".length());
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static class JsonData {
        String variableName;
        int sourceType;
        int accessorVariableId;

        JsonData(String variableName, int sourceType, int accessorVariableId) {
            this.variableName = variableName;
            this.sourceType = sourceType;
            this.accessorVariableId = accessorVariableId;
        }
    }
}
