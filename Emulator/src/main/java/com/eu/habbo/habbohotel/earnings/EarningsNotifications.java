package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.earnings.IncomeRewardNotificationComposer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The official {@code IncomeRewardNotification}: sent when a new reward lands in the earnings window, so AIR's
 * {@code EarningsController} posts {@code notification.earning.new} (linking {@code habboUI/open/vault}) and lights
 * the purse's earnings indicator. A claim is not a new reward and sends nothing.
 *
 * <p>Sources: a marketplace sale of an online seller, a step of achievement score or a new talent level that opens a
 * new claim, and wired credits ({@link WiredEarnings}). Rows that a period opens (daily gift and the configured
 * periodic rewards) have no moment to announce; the client lights the indicator from the first status it reads.
 */
public final class EarningsNotifications {
    private static final Logger LOGGER = LoggerFactory.getLogger(EarningsNotifications.class);

    private static final AtomicReference<Supplier<EarningsCenterManager>> MANAGER =
            new AtomicReference<>(EarningsCenterManager::new);

    private EarningsNotifications() {}

    /** Swap the manager source (tests). Null puts the hotel's back. */
    public static void use(Supplier<EarningsCenterManager> manager) {
        MANAGER.set(manager == null ? EarningsCenterManager::new : manager);
    }

    /** AIR {@code EarningsView._rewardCategories} index of a row (the byte of the notification). */
    public static int airCategory(EarningsCategory category) {
        if (category == null) return 0;
        return switch (category) {
            case DAILY_GIFT -> 1;
            case ACHIEVEMENTS -> 2;
            case MARKETPLACE -> 3;
            case HC_PAYDAY -> 4;
            case LEVEL_PROGRESS -> 5;
            case BONUS_BAG -> 7;
            case DONATIONS -> 8;
            case MYSTERY_BOXES -> 9;
            case GAMES -> WiredEarnings.CATEGORY_GAMES;
            case AGENCY -> WiredEarnings.CATEGORY_AGENCY;
            case CLUB_JOB -> 0;
        };
    }

    /** Tell an online player that {@code category} holds a new reward, when the window would let them claim it. */
    public static boolean newReward(Habbo habbo, EarningsCategory category) {
        if (habbo == null || habbo.getClient() == null || habbo.getHabboInfo() == null) return false;

        try {
            if (!MANAGER.get().get().isClaimable(habbo, category)) return false;
        } catch (RuntimeException e) {
            LOGGER.debug("Earnings state unavailable for the new reward notification", e);
            return false;
        }

        habbo.getClient().sendResponse(new IncomeRewardNotificationComposer(airCategory(category)));
        return true;
    }

    /** The claim period of a progress row before a change; pass it to {@link #afterProgress}. */
    public static String period(Habbo habbo, EarningsCategory category) {
        try {
            return MANAGER.get().get().progressPeriod(habbo, category);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** After a change of score or level: a new claim period that is claimable is a new reward. */
    public static boolean afterProgress(Habbo habbo, EarningsCategory category, String periodBefore) {
        if (periodBefore == null) return false;
        if (Objects.equals(periodBefore, period(habbo, category))) return false;
        return newReward(habbo, category);
    }
}
