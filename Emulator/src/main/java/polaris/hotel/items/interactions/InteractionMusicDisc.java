package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionMusicDisc extends UserItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionMusicDisc.class);

    private int songId;

    public InteractionMusicDisc(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        String[] stuff = this.getExtradata().split("\n");

        if (stuff.length >= 7 && !stuff[6].isEmpty()) {
            try {
                this.songId = Integer.parseInt(stuff[6]);
            } catch (Exception e) {
                LOGGER.error("Warning: Item {} has an invalid song id set for its music disk!", this.getId());
            }
        }
    }

    public InteractionMusicDisc(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        String[] stuff = this.getExtradata().split("\n");

        if (stuff.length >= 7 && !stuff[6].isEmpty()) {
            try {
                this.songId = Integer.parseInt(stuff[6]);
            } catch (Exception e) {
                LOGGER.error("Warning: Item {} has an invalid song id set for its music disk!", this.getId());
            }
        }
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
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

    public int getSongId() {
        return this.songId;
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        room.getTraxManager().sendUpdatedSongList();
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);

        room.getTraxManager().sendUpdatedSongList();
    }
}
