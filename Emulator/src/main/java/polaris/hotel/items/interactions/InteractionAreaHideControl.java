package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomAreaHideSupport;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.messages.ServerMessage;

public class InteractionAreaHideControl extends InteractionCustomValues {
    public static final Map<String, String> defaultValues = new HashMap<String, String>() {
        {
            this.put("state", "0");
        }

        {
            this.put("rootX", "0");
        }

        {
            this.put("rootY", "0");
        }

        {
            this.put("width", "0");
        }

        {
            this.put("length", "0");
        }

        {
            this.put("invisibility", "0");
        }

        {
            this.put("wallItems", "0");
        }

        {
            this.put("invert", "0");
        }
    };

    public InteractionAreaHideControl(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, defaultValues);
        this.normalizeValues();
    }

    public InteractionAreaHideControl(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, defaultValues);
        this.normalizeValues();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        this.normalizeValues();

        serverMessage.appendInt(5 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(8);
        serverMessage.appendInt(RoomAreaHideSupport.getState(this));
        serverMessage.appendInt(RoomAreaHideSupport.getRootX(this));
        serverMessage.appendInt(RoomAreaHideSupport.getRootY(this));
        serverMessage.appendInt(RoomAreaHideSupport.getWidth(this));
        serverMessage.appendInt(RoomAreaHideSupport.getLength(this));
        serverMessage.appendInt(RoomAreaHideSupport.isInvisibilityEnabled(this) ? 1 : 0);
        serverMessage.appendInt(RoomAreaHideSupport.includesWallItems(this) ? 1 : 0);
        serverMessage.appendInt(RoomAreaHideSupport.isInverted(this) ? 1 : 0);

        if (this.isLimited()) {
            serverMessage.appendInt(this.getLimitedSells());
            serverMessage.appendInt(this.getLimitedStack());
        }
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public boolean allowWiredResetState() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room == null) {
            return;
        }

        boolean wiredToggle = objects != null && objects.length >= 2 && objects[1] instanceof WiredEffectType;

        if (!wiredToggle) {
            if (client == null || !this.canToggle(client.getUser(), room)) {
                return;
            }
        }

        this.values.put("state", (RoomAreaHideSupport.getState(this) == 1) ? "0" : "1");
        this.normalizeValues();
        this.needsUpdate(true);
        Emulator.getThreading().run(this);
        room.updateItem(this);
    }

    @Override
    public void onCustomValuesSaved(Room room, GameClient client, Map<String, String> oldValues) {
        this.normalizeValues();
    }

    public boolean canToggle(User user, Room room) {
        if (user == null || room == null) {
            return false;
        }

        if (room.hasRights(user)) {
            return true;
        }

        if (!user.getUserStats().isRentingSpace()) {
            return false;
        }

        UserItem rentedItem = room.getUserItem(user.getUserStats().rentedItemId);

        return room.getLayout() != null
                && rentedItem != null
                && RoomLayout.squareInSquare(
                        RoomLayout.getRectangle(
                                rentedItem.getX(),
                                rentedItem.getY(),
                                rentedItem.getBaseItem().getWidth(),
                                rentedItem.getBaseItem().getLength(),
                                rentedItem.getRotation()),
                        RoomLayout.getRectangle(
                                this.getX(),
                                this.getY(),
                                this.getBaseItem().getWidth(),
                                this.getBaseItem().getLength(),
                                this.getRotation()));
    }

    private void normalizeValues() {
        this.values.put("state", booleanFlag(this.values.get("state")));
        this.values.put("rootX", Integer.toString(nonNegative(this.values.get("rootX"))));
        this.values.put("rootY", Integer.toString(nonNegative(this.values.get("rootY"))));
        this.values.put("width", Integer.toString(nonNegative(this.values.get("width"))));
        this.values.put("length", Integer.toString(nonNegative(this.values.get("length"))));
        this.values.put("invisibility", booleanFlag(this.values.get("invisibility")));
        this.values.put("wallItems", booleanFlag(this.values.get("wallItems")));
        this.values.put("invert", booleanFlag(this.values.get("invert")));
    }

    private static int nonNegative(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String booleanFlag(String value) {
        return ("1".equals(value) || "true".equalsIgnoreCase(value)) ? "1" : "0";
    }
}
