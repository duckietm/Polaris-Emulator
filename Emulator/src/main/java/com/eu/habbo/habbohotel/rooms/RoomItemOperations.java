package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionMultiHeight;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WallItemUpdateComposer;

final class RoomItemOperations {

    private final Room room;

    RoomItemOperations(Room room) {
        this.room = room;
    }

    void updateItem(HabboItem item) {
        if (!this.room.isLoaded()
                || item == null
                || item.getRoomId() != this.room.getId()
                || item.getBaseItem() == null) {
            return;
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
            this.room.updateTiles(this.room
                    .getLayout()
                    .getTilesAt(
                            this.room.currentLayout().getTile(item.getX(), item.getY()),
                            item.getBaseItem().getWidth(),
                            item.getBaseItem().getLength(),
                            item.getRotation()));

            if (RoomAreaHideSupport.isControllerItem(item)) {
                RoomAreaHideSupport.sendState(this.room, item);
            }
            this.room.onFurnitureTopologyChanged();
        } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.room.sendComposer(new WallItemUpdateComposer(item).compose());
        }
    }

    void updateItemState(HabboItem item) {
        if (item == null) {
            return;
        }

        if (RoomAreaHideSupport.isControllerItem(item)) {
            this.updateItem(item);
            return;
        }

        if (!item.isLimited()) {
            this.room.sendComposer(new ItemStateComposer(item).compose());
        } else {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (this.room.currentLayout() == null) {
                return;
            }

            this.room.updateTiles(this.room
                    .getLayout()
                    .getTilesAt(
                            this.room.currentLayout().getTile(item.getX(), item.getY()),
                            item.getBaseItem().getWidth(),
                            item.getBaseItem().getLength(),
                            item.getRotation()));

            if (item instanceof InteractionMultiHeight multiHeight) {
                multiHeight.updateUnitsOnItem(this.room);
            }
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR
                && (RoomConfInvisSupport.isControllerItem(item) || RoomConfInvisSupport.isTarget(item))) {
            RoomConfInvisSupport.sendState(this.room);
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR && RoomHanditemBlockSupport.isControllerItem(item)) {
            RoomHanditemBlockSupport.sendState(this.room);
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.onFurnitureTopologyChanged();
        }
    }
}
