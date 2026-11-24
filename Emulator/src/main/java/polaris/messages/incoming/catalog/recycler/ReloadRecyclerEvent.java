package polaris.messages.incoming.catalog.recycler;

import polaris.hotel.catalog.RecyclerRules;
import polaris.hotel.items.ItemManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.ReloadRecyclerComposer;

public class ReloadRecyclerEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.client.getUser().getUserInfo().getId();

        this.client.sendResponse(
                ReloadRecyclerComposer.of(ItemManager.RECYCLER_ENABLED, RecyclerRules.secondsToWait(userId)));
    }
}
