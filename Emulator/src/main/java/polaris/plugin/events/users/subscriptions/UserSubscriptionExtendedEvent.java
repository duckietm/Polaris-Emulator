package polaris.plugin.events.users.subscriptions;

import polaris.hotel.users.subscriptions.Subscription;
import polaris.plugin.Event;

public class UserSubscriptionExtendedEvent extends Event {
    public final int userId;
    public final Subscription subscription;
    public final int duration;

    public UserSubscriptionExtendedEvent(int userId, Subscription subscription, int duration) {
        super();
        this.userId = userId;
        this.subscription = subscription;
        this.duration = duration;
    }
}
