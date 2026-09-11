package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.items.interactions.StackHelperExtradata;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.UpdateStackHeightTileHeightComposer;
import java.util.Set;

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
        int itemId = this.packet.readInt();
        boolean moveDown = this.packet.readBoolean();

        Habbo habbo = this.client.getHabbo();
        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        if (habbo.getHabboInfo().getId() != room.getOwnerId() && !room.hasRights(habbo)) {
            return;
        }

        HabboItem item = room.getHabboItem(itemId);

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
                room.updateHabbosAt(tile.x, tile.y);
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
