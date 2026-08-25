package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public class WiredEffectUsersByType extends InteractionWiredEffect {
    private static final int ENTITY_HABBO = 1;
    private static final int ENTITY_PET = 2;
    private static final int ENTITY_BOT = 4;

    public static final WiredEffectType type = WiredEffectType.USERS_BY_TYPE_SELECTOR;

    private int entityType = ENTITY_HABBO;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersByType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        Set<RoomUnit> result = new LinkedHashSet<>();

        for (RoomUnit roomUnit : room.getRoomUnits()) {
            if (this.matchesType(roomUnit)) {
                result.add(roomUnit);
            }
        }

        result = this.applySelectorModifiers(result, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.entityType = (params.length > 0) ? this.normalizeEntityType(params[0]) : ENTITY_HABBO;
        this.filterExisting = params.length > 1 && params[1] == 1;
        this.invert = params.length > 2 && params[2] == 1;
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean isSelector() {
        return true;
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.entityType, this.filterExisting, this.invert, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.entityType = this.normalizeEntityType(data.entityType);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.entityType = ENTITY_HABBO;
        this.filterExisting = false;
        this.invert = false;
        this.setDelay(0);
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
        message.appendInt(this.entityType);
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private int normalizeEntityType(int value) {
        switch (value) {
            case ENTITY_PET:
            case ENTITY_BOT:
                return value;
            default:
                return ENTITY_HABBO;
        }
    }

    private boolean matchesType(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        RoomUnitType roomUnitType = roomUnit.getRoomUnitType();
        return roomUnitType != null && roomUnitType.getTypeId() == this.entityType;
    }

    static class JsonData {
        int entityType;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int entityType, boolean filterExisting, boolean invert, int delay) {
            this.entityType = entityType;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
