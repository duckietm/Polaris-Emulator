package polaris.messages.incoming.crafting;

import polaris.Emulator;
import polaris.hotel.crafting.CraftingAltar;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.crafting.CraftableProductsComposer;

public class RequestCraftingRecipesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        UserItem item = room.getUserItem(itemId);

        if (item != null) {
            CraftingAltar altar =
                    Emulator.getGameEnvironment().getCraftingManager().getAltar(item.getBaseItem());

            if (altar != null) {
                this.client.sendResponse(new CraftableProductsComposer(
                        altar.getRecipesForUser(this.client.getUser()), altar.getIngredients()));
            }
        }
    }
}
