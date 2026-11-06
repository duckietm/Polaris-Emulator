package polaris.hotel.items.interactions.totems;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;

public class InteractionTotemLegs extends InteractionDefault {
    public InteractionTotemLegs(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionTotemLegs(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public TotemType getTotemType() {
        int extraData;
        try {
            extraData = Integer.parseInt(this.getExtradata());
        } catch (NumberFormatException ex) {
            extraData = 0;
        }
        return TotemType.fromInt((int) Math.ceil((extraData + 1) / 4.0f));
    }

    public TotemColor getTotemColor() {
        int extraData;
        try {
            extraData = Integer.parseInt(this.getExtradata());
        } catch (NumberFormatException ex) {
            extraData = 0;
        }
        return TotemColor.fromInt(extraData - (4 * (getTotemType().type - 1)));
    }

    private void updateHead(Room room, RoomTile tile) {
        for (UserItem item : room.getItemsAt(tile)) {
            if (item instanceof InteractionTotemHead && item.getZ() > this.getZ())
                ((InteractionTotemHead) item).updateTotemState(room);
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (room == null
                || !((client != null && room.hasRights(client.getUser()))
                        || (objects.length >= 2 && objects[1] instanceof WiredEffectType))) return;

        updateHead(room, room.getLayout().getTile(this.getX(), this.getY()));
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);

        updateHead(room, oldLocation);
    }
}
