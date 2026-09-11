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
            if (this.disposed) {
                this.disposed = false;
                this.run();
            }
        } else {
            this.disposed = true;
        }
    }

    @Override
    public void run() {
        super.run();

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

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }
}
