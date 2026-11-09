package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.quests.QuestGoalType;
import polaris.hotel.quests.QuestProgressEvents;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserDataComposer;

/**
 * Official {@code SessionDataManager.replenishRespect()} (header 3728): the avatar menu offers
 * the row when the daily respects are spent and a replenish is left. The official client charges
 * duckets ({@code respect.replenish_cost_duckets}) before asking, so the server takes them here.
 */
public class ReplenishRespectEvent extends MessageHandler {
    static final String REASON = "economy.respect.replenish";

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();

        if (user == null || user.getUserStats().respectReplenishesLeft <= 0) {
            return;
        }

        if (!replenish(
                user,
                Emulator.getConfig().getInt("respect.replenish_cost_duckets", 50),
                Emulator.getConfig().getInt("hotel.daily.respect"))) {
            return;
        }

        this.client.sendResponse(new UserDataComposer(user));
        QuestProgressEvents.progress(user, QuestGoalType.REPLENISH_RESPECT, 1);
    }

    /**
     * Takes the duckets first and replenishes only when the debit went through: the replenish used to be written
     * before a void debit that a plugin or the ledger could refuse. A replenish that is no longer available (a second
     * packet spent it) gives the duckets back.
     */
    static boolean replenish(User user, int cost, int maxRespectPerDay) {
        int userId = user.getUserInfo().getId();
        if (cost > 0 && user.getUserInfo().getPixels() < cost) {
            return false;
        }
        if (cost > 0
                && !user.tryTakePoints(
                        0, cost, REASON, EconomyOperationId.create("respect_replenish:" + userId), userId)) {
            return false;
        }

        if (!user.getUserStats().replenishRespect(maxRespectPerDay)) {
            if (cost > 0) {
                user.givePixels(cost, REASON);
            }
            return false;
        }
        return true;
    }
}
