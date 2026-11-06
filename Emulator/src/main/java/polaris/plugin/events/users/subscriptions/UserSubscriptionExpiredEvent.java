package polaris.plugin.events.users.subscriptions;

import polaris.hotel.users.subscriptions.Subscription;
import polaris.plugin.Event;

public class UserSubscriptionExpiredEvent extends Event {
    public final int userId;
    public final Subscription subscription;

    public UserSubscriptionExpiredEvent(int userId, Subscription subscription) {
        super();

        this.userId = userId;
        this.subscription = subscription;
    }
}
