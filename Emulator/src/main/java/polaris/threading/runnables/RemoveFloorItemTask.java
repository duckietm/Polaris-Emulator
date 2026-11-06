package polaris.threading.runnables;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;

class RemoveFloorItemTask implements Runnable {
    private final Room room;
    private final UserItem item;

    public RemoveFloorItemTask(Room room, UserItem item) {
        this.room = room;
        this.item = item;
    }

    @Override
    public void run() {
        if (this.item == null || this.room == null) return;

        RoomTile tile = this.room.getLayout().getTile(this.item.getX(), this.item.getY());
        this.room.removeUserItem(this.item);
        this.room.updateTile(tile);
        this.room.sendComposer(new RemoveFloorItemComposer(this.item, true).compose());
        this.room.sendComposer(
                new UpdateStackHeightComposer(this.item.getX(), this.item.getY(), tile.z, tile.relativeHeight())
                        .compose());
    }
}
