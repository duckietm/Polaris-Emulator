package polaris.messages.incoming.rooms.items;

import java.util.Set;
import polaris.hotel.items.interactions.InteractionStackHelper;
import polaris.hotel.items.interactions.InteractionStackWalkHelper;
import polaris.hotel.items.interactions.InteractionTileWalkMagic;
import polaris.hotel.items.interactions.StackHelperExtradata;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.UpdateStackHeightTileHeightComposer;

/**
 * Official {@code CustomStackHeightWidget.sendAdjacentHeightRequest} (AIR 13,
 * header 2687): the two arrows beside the slider nudge a stack helper to the
 * next height instead of dragging the slider. The wire carries the furni id and
 * a boolean that is true for "move down".
 *
 * <p>The step is one hundredth, the unit the stack-height protocol itself uses
 * ({@code SetStackHelperHeight} sends {@code height * 100}), so a click moves
 * the helper by exactly one value the slider can express.
 */
public class SetStackHelperAdjacentHeightEvent extends MessageHandler {

    /** One protocol unit: heights travel as hundredths. */
    static final double STEP = 0.01D;

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        boolean moveDown = this.packet.readBoolean();

        User user = this.client.getUser();
        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        if (user.getUserInfo().getId() != room.getOwnerId() && !room.hasRights(user)) {
            return;
        }

        // Playtest (the hotel's own rule beyond AIR): the helper keeps its height, as for a visitor without rights.
        if (RoomPlayTestSupport.isBuildingRefused(user)) {
            return;
        }

        UserItem item = room.getUserItem(itemId);

        if (!(item instanceof InteractionStackHelper)
                && !(item instanceof InteractionTileWalkMagic)
                && !(item instanceof InteractionStackWalkHelper)) {
            return;
        }

        RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
        double height = item.getZ() + (moveDown ? -STEP : STEP);
        height = Math.min(Math.max(height, itemTile.z), Room.MAXIMUM_FURNI_HEIGHT);
        height = Math.round(height * 100.0D) / 100.0D;

        Set<RoomTile> tiles = room.getLayout()
                .getTilesAt(
                        itemTile,
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        item.getRotation());

        for (RoomTile tile : tiles) {
            tile.setStackHeight(height);
        }

        item.setZ(height);
        item.setExtradata(StackHelperExtradata.write(item.getExtradata(), height));
        item.needsUpdate(true);

        if (item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper) {
            for (RoomTile tile : tiles) {
                room.updateUsersAt(tile.x, tile.y);
                room.updateBotsAt(tile.x, tile.y);
                room.updatePetsAt(tile.x, tile.y);
            }
        }

        room.updateItem(item);
        room.updateTiles(tiles);
        room.sendComposer(new UpdateStackHeightComposer(room, tiles).compose());
        room.sendComposer(new UpdateStackHeightTileHeightComposer(item, (int) (height * 100)).compose());
    }
}
