package polaris.messages.incoming.collectibles;

import java.util.List;
import java.util.Optional;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectionRewardClaimResultComposer;

/**
 * AIR 15 completion reward claim (official 2209, taken here by the mannequin: 9420,
 * {@code collectionId, wallet}): the reward for completing a set, once per user. Answers
 * {@code 501 (collectionId, wallet, success)}.
 */
public class ClaimCollectionRewardEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        String collectionId = this.packet.readString();
        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        Optional<Owned> owned = Optional.empty();
        if (CollectiblesSupport.ownsWallet(user, wallet)) {
            owned = service.claimCollection(user.getUserInfo().getId(), collectionId, false, CollectiblesSupport.now());
        }
        if (owned.isPresent()) CollectiblesSupport.publishAcquired(user, List.of(owned.get()), service, settings);

        this.client.sendResponse(new CollectionRewardClaimResultComposer(collectionId, wallet, owned.isPresent()));
    }
}
