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
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.UpdateStackHeightTileHeightComposer;

public class SetStackHelperHeightEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        // Playtest (the hotel's own rule beyond AIR): the helper keeps its height, as for a visitor without rights.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) return;

        if (this.client.getUser().getUserInfo().getId()
                        == this.client.getUser().getUserInfo().getCurrentRoom().getOwnerId()
                || this.client.getUser().getUserInfo().getCurrentRoom().hasRights(this.client.getUser())) {
            UserItem item = this.client.getUser().getUserInfo().getCurrentRoom().getUserItem(itemId);

            if (item instanceof InteractionStackHelper
                    || item instanceof InteractionTileWalkMagic
                    || item instanceof InteractionStackWalkHelper) {
                Room room = this.client.getUser().getUserInfo().getCurrentRoom();
                RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
                double stackerHeight = this.packet.readInt();

                // Official `CustomStackHeightWidget.onMultiWalkChange` (AIR 13) appends the
                // multi-walk checkbox to this same composer; the plain slider update omits it.
                boolean multiWalk = this.packet.bytesAvailable() > 0
                        ? this.packet.readBoolean()
                        : StackHelperExtradata.multiWalk(item.getExtradata());

                Set<RoomTile> tiles = room.getLayout()
                        .getTilesAt(
                                itemTile,
                                item.getBaseItem().getWidth(),
                                item.getBaseItem().getLength(),
                                item.getRotation());
                if (stackerHeight == -100) {
                    for (RoomTile tile : tiles) {
                        double stackheight = room.getStackHeight(tile.x, tile.y, false, item) * 100;
                        if (stackheight > stackerHeight) {
                            stackerHeight = stackheight;
                        }
                    }
                } else {
                    stackerHeight =
                            Math.min(Math.max(stackerHeight, itemTile.z * 100), Room.MAXIMUM_FURNI_HEIGHT * 100);
                }

                double height = 0;
                if (stackerHeight >= 0) {
                    height = stackerHeight / 100.0D;
                }

                for (RoomTile tile : tiles) {
                    tile.setStackHeight(height);
                }

                item.setZ(height);
                item.setExtradata(StackHelperExtradata.write(height, multiWalk));
                item.needsUpdate(true);

                if (item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper) {
                    for (RoomTile t : tiles) {
                        this.client.getUser().getUserInfo().getCurrentRoom().updateUsersAt(t.x, t.y);
                        this.client.getUser().getUserInfo().getCurrentRoom().updateBotsAt(t.x, t.y);
                        this.client.getUser().getUserInfo().getCurrentRoom().updatePetsAt(t.x, t.y);
                    }
                }

                this.client.getUser().getUserInfo().getCurrentRoom().updateItem(item);
                this.client.getUser().getUserInfo().getCurrentRoom().updateTiles(tiles);
                this.client
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(new UpdateStackHeightComposer(room, tiles).compose());
                this.client
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(new UpdateStackHeightTileHeightComposer(item, (int) ((height) * 100)).compose());
            }
        }
    }
}
