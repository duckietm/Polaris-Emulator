package polaris.messages.incoming.collectibles;

import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleTransferFeeComposer;

/**
 * AIR 15 {@code class_3623} transfer fee request (official 875, taken here by the floor plan editor:
 * 9421). Answers {@code 3909}: the silver a transfer of every collectible costs.
 */
public class GetCollectibleTransferFeeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        int count = service.owned(user.getUserInfo().getId()).size();
        this.client.sendResponse(new CollectibleTransferFeeComposer(TransferCollectiblesEvent.fee(count, settings)));
    }
}
