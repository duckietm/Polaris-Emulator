package com.eu.habbo.messages.incoming.wheel;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wheel.WheelManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wheel.WheelDataComposer;

public class WheelBuySpinEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        WheelManager wheel = Emulator.getGameEnvironment().getWheelManager();
        wheel.buySpin(habbo); // whether or not it succeeds, resend the balance

        this.client.sendResponse(new WheelDataComposer(
                wheel.getUserState(habbo.getHabboInfo().getId()),
                wheel.getSpinCost(), wheel.getSpinCostType(), wheel.getPrizes()));
    }
}
