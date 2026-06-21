package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraMovePhysics extends InteractionWiredExtra {
    public static final int CODE = 61;
    public static final int SOURCE_ALL_ROOM = 900;

    private boolean keepAltitude = false;
    private boolean moveThroughFurni = false;
    private boolean moveThroughUsers = false;
    private boolean blockByFurni = false;
    private int moveThroughFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int moveThroughUsersSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int blockByFurniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredExtraMovePhysics(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMovePhysics(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.keepAltitude = readFlag(params, 0);
        this.moveThroughFurni = readFlag(params, 1);
        this.moveThroughUsers = readFlag(params, 2);
        this.blockByFurni = readFlag(params, 3);
        this.moveThroughFurniSource = normalizeSource(readInt(params, 4, WiredSourceUtil.SOURCE_TRIGGER));
        this.blockByFurniSource = normalizeSource(readInt(params, 5, WiredSourceUtil.SOURCE_TRIGGER));
        this.moveThroughUsersSource = normalizeSource(readInt(params, 6, WiredSourceUtil.SOURCE_TRIGGER));

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.keepAltitude,
                this.moveThroughFurni,
                this.moveThroughUsers,
                this.blockByFurni,
                this.moveThroughFurniSource,
                this.blockByFurniSource,
                this.moveThroughUsersSource));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(7);
        message.appendInt(this.keepAltitude ? 1 : 0);
        message.appendInt(this.moveThroughFurni ? 1 : 0);
        message.appendInt(this.moveThroughUsers ? 1 : 0);
        message.appendInt(this.blockByFurni ? 1 : 0);
        message.appendInt(this.moveThroughFurniSource);
        message.appendInt(this.blockByFurniSource);
        message.appendInt(this.moveThroughUsersSource);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.keepAltitude = data.keepAltitude;
                this.moveThroughFurni = data.moveThroughFurni;
                this.moveThroughUsers = data.moveThroughUsers;
                this.blockByFurni = data.blockByFurni;
                this.moveThroughFurniSource = normalizeSource(data.moveThroughFurniSource);
                this.blockByFurniSource = normalizeSource(data.blockByFurniSource);
                this.moveThroughUsersSource = normalizeSource(data.moveThroughUsersSource);
            }

            return;
        }

        String[] legacyData = wiredData.split("\t");
        this.keepAltitude = readLegacyFlag(legacyData, 0);
        this.moveThroughFurni = readLegacyFlag(legacyData, 1);
        this.moveThroughUsers = readLegacyFlag(legacyData, 2);
        this.blockByFurni = readLegacyFlag(legacyData, 3);
        this.moveThroughFurniSource = normalizeSource(readLegacyInt(legacyData, 4, WiredSourceUtil.SOURCE_TRIGGER));
        this.blockByFurniSource = normalizeSource(readLegacyInt(legacyData, 5, WiredSourceUtil.SOURCE_TRIGGER));
        this.moveThroughUsersSource = normalizeSource(readLegacyInt(legacyData, 6, WiredSourceUtil.SOURCE_TRIGGER));
    }

    @Override
    public void onPickUp() {
        this.keepAltitude = false;
        this.moveThroughFurni = false;
        this.moveThroughUsers = false;
        this.blockByFurni = false;
        this.moveThroughFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.moveThroughUsersSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.blockByFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public boolean isKeepAltitude() {
        return this.keepAltitude;
    }

    public boolean isMoveThroughFurni() {
        return this.moveThroughFurni;
    }

    public boolean isMoveThroughUsers() {
        return this.moveThroughUsers;
    }

    public boolean isBlockByFurni() {
        return this.blockByFurni;
    }

    public int getMoveThroughFurniSource() {
        return this.moveThroughFurniSource;
    }

    public int getMoveThroughUsersSource() {
        return this.moveThroughUsersSource;
    }

    public int getBlockByFurniSource() {
        return this.blockByFurniSource;
    }

    private static boolean readFlag(int[] params, int index) {
        return readInt(params, index, 0) == 1;
    }

    private static int readInt(int[] params, int index, int fallback) {
        return (params.length > index) ? params[index] : fallback;
    }

    private static boolean readLegacyFlag(String[] data, int index) {
        return readLegacyInt(data, index, 0) == 1;
    }

    private static int readLegacyInt(String[] data, int index, int fallback) {
        if (data.length <= index) {
            return fallback;
        }

        try {
            return Integer.parseInt(data[index]);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int normalizeSource(int value) {
        switch (value) {
            case SOURCE_ALL_ROOM:
            case WiredSourceUtil.SOURCE_TRIGGER:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    static class JsonData {
        boolean keepAltitude;
        boolean moveThroughFurni;
        boolean moveThroughUsers;
        boolean blockByFurni;
        int moveThroughFurniSource;
        int blockByFurniSource;
        int moveThroughUsersSource;

        JsonData(boolean keepAltitude, boolean moveThroughFurni, boolean moveThroughUsers, boolean blockByFurni, int moveThroughFurniSource, int blockByFurniSource, int moveThroughUsersSource) {
            this.keepAltitude = keepAltitude;
            this.moveThroughFurni = moveThroughFurni;
            this.moveThroughUsers = moveThroughUsers;
            this.blockByFurni = blockByFurni;
            this.moveThroughFurniSource = moveThroughFurniSource;
            this.blockByFurniSource = blockByFurniSource;
            this.moveThroughUsersSource = moveThroughUsersSource;
        }
    }
}
