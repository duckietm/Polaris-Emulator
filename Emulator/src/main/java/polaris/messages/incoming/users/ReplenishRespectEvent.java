package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserDataComposer;

/**
 * Official {@code SessionDataManager.replenishRespect()} (header 3728): the avatar menu offers
 * the row when the daily respects are spent and a replenish is left. The official client charges
 * duckets ({@code respect.replenish_cost_duckets}) before asking, so the server takes them here.
 */
public class ReplenishRespectEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();

        if (user == null || user.getUserStats().respectReplenishesLeft <= 0) {
            return;
        }

        int cost = Emulator.getConfig().getInt("respect.replenish_cost_duckets", 50);

        if (cost > 0 && user.getUserInfo().getPixels() < cost) {
            return;
        }

        if (!user.getUserStats().replenishRespect(Emulator.getConfig().getInt("hotel.daily.respect"))) {
            return;
        }

        if (cost > 0) {
            user.givePixels(-cost, "economy.respect.replenish");
        }

        this.client.sendResponse(new UserDataComposer(user));
        polaris.hotel.quests.QuestProgressEvents.progress(
                user, polaris.hotel.quests.QuestGoalType.REPLENISH_RESPECT, 1);
    }
}
