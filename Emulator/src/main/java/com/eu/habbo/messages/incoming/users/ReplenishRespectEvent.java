package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;

/**
 * Official {@code SessionDataManager.replenishRespect()} (header 3728): the avatar menu offers
 * the row when the daily respects are spent and a replenish is left. The official client charges
 * duckets ({@code respect.replenish_cost_duckets}) before asking, so the server takes them here.
 */
public class ReplenishRespectEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getHabboStats().respectReplenishesLeft <= 0) {
            return;
        }

        int cost = Emulator.getConfig().getInt("respect.replenish_cost_duckets", 50);

        if (cost > 0 && habbo.getHabboInfo().getPixels() < cost) {
            return;
        }

        if (!habbo.getHabboStats().replenishRespect(Emulator.getConfig().getInt("hotel.daily.respect"))) {
            return;
        }

        if (cost > 0) {
            habbo.givePixels(-cost, "economy.respect.replenish");
        }

        this.client.sendResponse(new UserDataComposer(habbo));
    }
}
