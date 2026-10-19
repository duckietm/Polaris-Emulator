package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredVariableAddonSharing;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The shared editor shape of the official Variable Add-on Share (1003) and Import (1004) boxes (AIR base
 * {@code §_-jt§}): a usage text and the "Categorieën" checkboxes, int params {@code [text connectors, subvariable
 * creators, variable fx]} as 0/1. What a category does is in {@link WiredVariableAddonSharing}.
 */
public abstract class WiredExtraVariableAddonSharing extends InteractionWiredExtra {
    private boolean textConnectors;
    private boolean subvariableCreators;
    private boolean variableFx;

    protected WiredExtraVariableAddonSharing(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredExtraVariableAddonSharing(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** The official addon code the editor packet carries. */
    protected abstract int getCode();

    /** Whether the box has {@code category} ({@link WiredVariableAddonSharing#TEXT_CONNECTORS} etc.) ticked. */
    public boolean allows(int category) {
        return switch (category) {
            case WiredVariableAddonSharing.TEXT_CONNECTORS -> this.textConnectors;
            case WiredVariableAddonSharing.SUBVARIABLE_CREATORS -> this.subvariableCreators;
            case WiredVariableAddonSharing.VARIABLE_FX -> this.variableFx;
            default -> false;
        };
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.textConnectors = params.length > 0 && params[0] == 1;
        this.subvariableCreators = params.length > 1 && params[1] == 1;
        this.variableFx = params.length > 2 && params[2] == 1;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.textConnectors, this.subvariableCreators, this.variableFx));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.textConnectors ? 1 : 0);
        message.appendInt(this.subvariableCreators ? 1 : 0);
        message.appendInt(this.variableFx ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getCode());
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;
        this.textConnectors = data.textConnectors;
        this.subvariableCreators = data.subvariableCreators;
        this.variableFx = data.variableFx;
    }

    @Override
    public void onPickUp() {
        this.textConnectors = false;
        this.subvariableCreators = false;
        this.variableFx = false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    static class JsonData {
        boolean textConnectors;
        boolean subvariableCreators;
        boolean variableFx;

        JsonData(boolean textConnectors, boolean subvariableCreators, boolean variableFx) {
            this.textConnectors = textConnectors;
            this.subvariableCreators = subvariableCreators;
            this.variableFx = variableFx;
        }
    }
}
