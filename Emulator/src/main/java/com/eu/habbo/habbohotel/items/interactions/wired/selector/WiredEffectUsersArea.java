package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSelector;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectUsersArea extends InteractionWiredEffect implements WiredOfficialSelector {

    public static final WiredEffectType type = WiredEffectType.USERS_AREA_SELECTOR;

    private int rootX = 0;
    private int rootY = 0;
    private int areaWidth = 0;
    private int areaHeight = 0;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersArea(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersArea(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || areaWidth <= 0 || areaHeight <= 0) return;

        int maxX = rootX + areaWidth - 1;
        int maxY = rootY + areaHeight - 1;

        List<RoomUnit> usersInArea = new ArrayList<>();
        for (RoomUnit unit : room.getRoomUnits()) {
            int x = unit.getX();
            int y = unit.getY();
            boolean inArea = x >= rootX && x <= maxX && y >= rootY && y <= maxY;
            if (inArea) {
                usersInArea.add(unit);
            }
        }

        ctx.targets()
                .setUsers(this.applySelectorModifiers(
                        usersInArea, room.getRoomUnits(), ctx.targets().users(), filterExisting, invert));
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        // AIR clamps each value to 0..64 and fills a value the save left out, instead of refusing it.
        this.rootX = WiredSelectorPayloadGuard.areaValue(params, 0);
        this.rootY = WiredSelectorPayloadGuard.areaValue(params, 1);
        this.areaWidth = WiredSelectorPayloadGuard.areaValue(params, 2);
        this.areaHeight = WiredSelectorPayloadGuard.areaValue(params, 3);
        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();
        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public boolean selectorFilter() {
        return this.filterExisting;
    }

    @Override
    public boolean selectorInvert() {
        return this.invert;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[0];
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
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
        return WiredManager.getGson()
                .toJson(new JsonData(rootX, rootY, areaWidth, areaHeight, filterExisting, invert, getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
            if (data == null) return;

            this.rootX = data.rootX;
            this.rootY = data.rootY;
            this.areaWidth = data.width;
            this.areaHeight = data.height;
            this.filterExisting = data.filterExisting;
            this.invert = data.invert;
            this.setDelay(data.delay);
        }
    }

    @Override
    public void onPickUp() {
        this.rootX = 0;
        this.rootY = 0;
        this.areaWidth = 0;
        this.areaHeight = 0;
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

        message.appendInt(4);
        message.appendInt(this.rootX);
        message.appendInt(this.rootY);
        message.appendInt(this.areaWidth);
        message.appendInt(this.areaHeight);

        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    public int getRootX() {
        return this.rootX;
    }

    public int getRootY() {
        return this.rootY;
    }

    public int getAreaWidth() {
        return this.areaWidth;
    }

    public int getAreaHeight() {
        return this.areaHeight;
    }

    static class JsonData {
        int rootX;
        int rootY;
        int width;
        int height;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int rootX, int rootY, int width, int height, boolean filterExisting, boolean invert, int delay) {
            this.rootX = rootX;
            this.rootY = rootY;
            this.width = width;
            this.height = height;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
