package polaris.hotel.items.interactions;

import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;

public class InteractionWaterItem extends InteractionMultiHeight {
    public InteractionWaterItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWaterItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onPlace(Room room) {
        this.update();
        super.onPlace(room);
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        this.setExtradata("0");
        this.needsUpdate(true);
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.update();
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, new Object[] {});
    }

    public void update() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            return;
        }

        Rectangle rectangle = this.getRectangle();

        // Check if every tile of the furni is in water.
        boolean foundWater = true;

        for (short x = (short) rectangle.x; x < rectangle.getWidth() + rectangle.x && foundWater; x++) {
            for (short y = (short) rectangle.y; y < rectangle.getHeight() + rectangle.y && foundWater; y++) {
                boolean tile = false;
                Set<UserItem> items = room.getItemsAt(room.getLayout().getTile(x, y));

                for (UserItem item : items) {
                    if (item instanceof InteractionWater) {
                        tile = true;
                        break;
                    }
                }

                if (!tile) {
                    foundWater = false;
                }
            }
        }

        // Update data if changed.
        String updatedData = foundWater ? "1" : "0";

        if (!this.getExtradata().equals(updatedData)) {
            this.setExtradata(updatedData);
            this.needsUpdate(true);
            room.updateItemState(this);
        }
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
