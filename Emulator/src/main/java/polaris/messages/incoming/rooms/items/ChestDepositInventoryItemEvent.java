package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestDepositSession;
import polaris.hotel.items.interactions.wired.chest.ChestFurniDepositHelper;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/** Deposit one inventory furni row into a wired furni chest [chestItemId, inventoryItemId]. Header 9325. */
public class ChestDepositInventoryItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int chestItemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int inventoryItemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        if (inventoryItemId <= 0) return;

        if (!ChestDepositSession.isDepositingInto(user.getUserInfo().getId(), chestItemId)) return;

        UserItem item = room.getUserItem(chestItemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        UserItem inventoryItem = user.getInventory().getItemsComponent().getUserItem(inventoryItemId);
        if (inventoryItem == null) return;

        ChestFurniDepositHelper.depositInventoryItem(user, chest, inventoryItem);
    }
}
