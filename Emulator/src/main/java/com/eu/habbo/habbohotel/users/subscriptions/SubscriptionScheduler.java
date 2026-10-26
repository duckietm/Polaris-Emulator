package com.eu.habbo.habbohotel.users.subscriptions;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.plugin.events.users.subscriptions.UserSubscriptionExpiredEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .entrySet()) {
            Habbo habbo = map.getValue();

            try {
                if (habbo != null) {
                    LedgerWalletMutation.coordinated(habbo, () -> {
                        synchronized (habbo.getHabboStats()) {
                            for (Subscription subscription : habbo.getHabboStats().subscriptions) {
                                if (subscription.isActive() && subscription.getRemaining() < 0) {
                                    if (!Emulator.getPluginManager()
                                            .fireEvent(new UserSubscriptionExpiredEvent(
                                                    habbo.getHabboInfo().getId(), subscription))
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

        if (SubscriptionHabboClub.HC_PAYDAY_ENABLED
                && !SubscriptionHabboClub.isExecuting
                && SubscriptionHabboClub.HC_PAYDAY_NEXT_DATE < Emulator.getIntUnixTimestamp()) {
            SubscriptionHabboClub.executePayDay();
        }
    }
}
