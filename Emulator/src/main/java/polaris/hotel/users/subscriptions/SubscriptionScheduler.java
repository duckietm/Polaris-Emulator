package polaris.hotel.users.subscriptions;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.Scheduler;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.plugin.events.users.subscriptions.UserSubscriptionExpiredEvent;

/**
 * @author Beny
 */
public class SubscriptionScheduler extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionScheduler.class);

    public SubscriptionScheduler() {
        super(Emulator.getConfig().getInt("subscriptions.scheduler.interval", 10));
        this.reloadConfig();
    }

    /**
     * Called when config is changed. Should end the scheduler if disabled.
     */
    public void reloadConfig() {
        if (Emulator.getConfig().getBoolean("subscriptions.scheduler.enabled", true)) {
            this.restart();
        } else {
            this.setDisposed(true);
        }
    }

    @Override
    protected void tick() {
        this.expireSubscriptions();
    }

    /**
     * Retires every subscription of every online user whose time has run out, and pays
     * the HC payday when it is due.
     *
     * <p>Separate from {@link #tick()} so it can be driven directly: the base class only
     * ticks an enabled scheduler, and this work is also what the RCON and command paths
     * race against.
     */
    public void expireSubscriptions() {
        for (Map.Entry<Integer, User> map :
                Emulator.getGameEnvironment().getUserManager().getOnlineUsers().entrySet()) {
            User user = map.getValue();

            try {
                if (user != null) {
                    LedgerWalletMutation.coordinated(user, () -> {
                        synchronized (user.getUserStats()) {
                            for (Subscription subscription : user.getUserStats().subscriptions) {
                                if (subscription.isActive() && subscription.getRemaining() < 0) {
                                    if (!Emulator.getPluginManager()
                                            .fireEvent(new UserSubscriptionExpiredEvent(
                                                    user.getUserInfo().getId(), subscription))
                                            .isCancelled()) {
                                        subscription.onExpired();
                                        subscription.setActive(false);
                                    }
                                }
                            }
                        }
                        return null;
                    });
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        if (SubscriptionClub.HC_PAYDAY_ENABLED
                && !SubscriptionClub.isExecuting
                && SubscriptionClub.HC_PAYDAY_NEXT_DATE < Emulator.getIntUnixTimestamp()) {
            SubscriptionClub.executePayDay();
        }
    }
}
