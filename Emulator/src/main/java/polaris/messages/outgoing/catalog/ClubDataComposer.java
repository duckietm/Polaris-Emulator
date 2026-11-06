package polaris.messages.outgoing.catalog;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.catalog.ClubOffer;
import polaris.hotel.users.User;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ClubDataComposer extends MessageComposer {
    private final int windowId;
    private final User user;

    public ClubDataComposer(User user, int windowId) {
        this.user = user;
        this.windowId = windowId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ClubDataComposer);

        List<ClubOffer> offers =
                Emulator.getGameEnvironment().getCatalogManager().getClubOffers(this.windowId);
        this.response.appendInt(offers.size());

        for (ClubOffer offer : offers) {
            int expireTimestamp = offer.isBuildersClubSubscription()
                    ? this.user.getUserStats().getSubscriptionExpireTimestamp(Subscription.BUILDERS_CLUB)
                    : (offer.isBuildersClubAddon()
                            ? Emulator.getIntUnixTimestamp()
                            : this.user.getUserStats().getClubExpireTimestamp());

            offer.serialize(this.response, expireTimestamp);
        }

        this.response.appendInt(this.windowId);
        return this.response;
    }

    public int getWindowId() {
        return windowId;
    }

    public User getUser() {
        return user;
    }
}
