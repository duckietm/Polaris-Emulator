package polaris.messages.incoming.collectibles;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.collectibles.CollectibleData.Mintable;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleMintablesComposer;

/** AIR 15 relic list request (1606): the furni that can be turned into relics ({@code 9423}). */
public class GetMintableItemsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        CollectiblesService service = CollectiblesSupport.service(CollectiblesSupport.settings());
        if (service == null) return;

        List<CollectibleMintablesComposer.Entry> entries = new ArrayList<>();
        for (Mintable mintable : service.mintables()) {
            Item base = CollectiblesSupport.baseItem(mintable.baseItemId());
            if (base == null) continue;
            entries.add(new CollectibleMintablesComposer.Entry(
                    mintable, base.getSpriteId(), base.getType() == FurnitureType.WALL));
        }
        this.client.sendResponse(new CollectibleMintablesComposer(entries));
    }
}
