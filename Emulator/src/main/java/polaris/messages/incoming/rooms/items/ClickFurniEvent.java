package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionPlant;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;

public class ClickFurniEvent extends MessageHandler {
    private static final String CLICK_TILE_INTERACTION = "room_invisible_click_tile";

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId)) {
            return;
        }

        UserItem item = room.getUserItem(itemId);

        if (item == null) {
            return;
        }

        WiredManager.queueUserClicksFurni(room, this.client.getUser().getRoomUnit(), item);

        // AIR 13 treasure hunt: the official client has no "find" packet - a find is a
        // click on one of the items the hunt hid, which answers TreasureHuntUpdate / Fail.
        if (Emulator.getConfig().getBoolean("hotel.treasurehunt.enabled", true)) {
            Emulator.getGameEnvironment().getTreasureHuntManager().onItemClicked(this.client.getUser(), itemId);
        }

        if (isClickTileItem(item)) {
            WiredManager.triggerUserClicksTile(room, this.client.getUser().getRoomUnit(), item);
        }

        if (item instanceof InteractionPlant) {
            item.onClick(this.client, room, new Object[] {0});
        }
    }

    private boolean isClickTileItem(UserItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interaction = item.getBaseItem().getInteractionType().getName();
        return interaction != null && interaction.equalsIgnoreCase(CLICK_TILE_INTERACTION);
    }
}
