package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;

public class WiredExtraMoveCarryUsers extends InteractionWiredExtra {
    public static final int CODE = 58;
    public static final int MODE_DIRECTLY_ON_FURNI = 0;
    public static final int MODE_SAME_TILE = 1;
    public static final int SOURCE_ALL_ROOM_USERS = 900;

    private int carryMode = MODE_DIRECTLY_ON_FURNI;
    // Officially everyone on the furni or tile is carried; our user source narrows that.
    private int userSource = SOURCE_ALL_ROOM_USERS;

    public WiredExtraMoveCarryUsers(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMoveCarryUsers(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.carryMode = this.normalizeCarryMode((params.length > 0) ? params[0] : MODE_DIRECTLY_ON_FURNI);
        this.userSource = this.normalizeUserSource((params.length > 1) ? params[1] : SOURCE_ALL_ROOM_USERS);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.carryMode, this.userSource));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.carryMode);
        message.appendInt(this.userSource);
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
                this.carryMode = this.normalizeCarryMode(data.carryMode);
                this.userSource = this.normalizeUserSource(data.userSource);
            }

            return;
        }

        String[] legacyData = wiredData.split("\t");
        if (legacyData.length > 0) {
            try {
                this.carryMode = this.normalizeCarryMode(Integer.parseInt(legacyData[0]));
            } catch (NumberFormatException ignored) {
                this.carryMode = MODE_DIRECTLY_ON_FURNI;
            }
        }

        if (legacyData.length > 1) {
            try {
                this.userSource = this.normalizeUserSource(Integer.parseInt(legacyData[1]));
            } catch (NumberFormatException ignored) {
                this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.carryMode = MODE_DIRECTLY_ON_FURNI;
        this.userSource = SOURCE_ALL_ROOM_USERS;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getCarryMode() {
        return this.carryMode;
    }

    public int getUserSource() {
        return this.userSource;
    }

    private int normalizeCarryMode(int value) {
        return (value == MODE_SAME_TILE) ? MODE_SAME_TILE : MODE_DIRECTLY_ON_FURNI;
    }

    private int normalizeUserSource(int value) {
        switch (value) {
            case SOURCE_ALL_ROOM_USERS:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
            case WiredSourceUtil.SOURCE_TRIGGER:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    static class JsonData {
        int carryMode;
        int userSource;

        JsonData(int carryMode, int userSource) {
            this.carryMode = carryMode;
            this.userSource = userSource;
        }
    }
}
