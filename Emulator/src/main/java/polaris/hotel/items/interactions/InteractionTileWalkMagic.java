package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionTileWalkMagic extends UserItem {
    public InteractionTileWalkMagic(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionTileWalkMagic(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        // Only the height reaches the client: the multi-walk flag lives behind a
        // suffix the official stuff data never carries.
        serverMessage.appendString(StackHelperExtradata.legacy(this.getExtradata()));

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}
