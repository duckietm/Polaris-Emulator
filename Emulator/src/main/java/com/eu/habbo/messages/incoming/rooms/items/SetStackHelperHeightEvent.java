package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.UpdateStackHeightTileHeightComposer;

import java.util.Set;

public class SetStackHelperHeightEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null)
            return;

        if (this.client.getHabbo().getHabboInfo().getId() == this.client.getHabbo().getHabboInfo().getCurrentRoom().getOwnerId() || this.client.getHabbo().getHabboInfo().getCurrentRoom().hasRights(this.client.getHabbo())) {
            HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(itemId);

            if (item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper) {
                Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
                RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
                double stackerHeight = this.packet.readInt();

                Set<RoomTile> tiles = room.getLayout().getTilesAt(itemTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
                if (stackerHeight == -100) {
                    for (RoomTile tile : tiles) {
                        double stackheight = room.getStackHeight(tile.x, tile.y, false, item) * 100;
                        if (stackheight > stackerHeight) {
                            stackerHeight = stackheight;
                        }
                    }
                } else {
                    stackerHeight = Math.min(Math.max(stackerHeight, itemTile.z * 100), Room.MAXIMUM_FURNI_HEIGHT * 100);
                }

                double height = 0;
                if (stackerHeight >= 0) {
                    height = stackerHeight / 100.0D;
                }

                for (RoomTile tile : tiles) {
                    tile.setStackHeight(height);
                }

                item.setZ(height);
                item.setExtradata((int) (height * 100) + "");
                item.needsUpdate(true);

                if (item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper) {
                    for (RoomTile t : tiles) {
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().updateHabbosAt(t.x, t.y);
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().updateBotsAt(t.x, t.y);
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().updatePetsAt(t.x, t.y);
                    }
                }

                this.client.getHabbo().getHabboInfo().getCurrentRoom().updateItem(item);
                this.client.getHabbo().getHabboInfo().getCurrentRoom().updateTiles(tiles);
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new UpdateStackHeightComposer(room, tiles).compose());
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new UpdateStackHeightTileHeightComposer(item, (int) ((height) * 100)).compose());
            }
        }
    }
}
