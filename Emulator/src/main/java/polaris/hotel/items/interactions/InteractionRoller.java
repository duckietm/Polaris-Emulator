package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionRoller extends UserItem {
    public static volatile boolean NO_RULES = false;
    public static int DELAY = 400;

    public InteractionRoller(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionRoller(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
    }

    @Override
    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<UserItem>>> itemsAtLocation) {
        if (NO_RULES) return true;
        if (itemsAtLocation.isEmpty()) return false;

        for (Pair<RoomTile, Set<UserItem>> set : itemsAtLocation) {
            if (set.getValue() != null && !set.getValue().isEmpty()) {
                if (set.getValue().size() > 1) {
                    return false;
                } else if (!set.getValue().contains(this)) {
                    return false;
                }
            }
        }

        return true;
    }
}
