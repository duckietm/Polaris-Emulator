package com.eu.habbo.messages.incoming.wheel;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wheel.WheelManager;
import com.eu.habbo.habbohotel.wheel.WheelPrize;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wheel.WheelDataComposer;
import com.eu.habbo.messages.outgoing.wheel.WheelRecentWinsComposer;
import com.eu.habbo.messages.outgoing.wheel.WheelResultComposer;

public class WheelSpinEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        WheelManager wheel = Emulator.getGameEnvironment().getWheelManager();
        WheelPrize prize = wheel.spin(habbo);

        if (prize != null) {
            this.client.sendResponse(new WheelResultComposer(prize.id));
        }

        // Refresh the balance either way so the client unlocks the wheel.
        this.client.sendResponse(new WheelDataComposer(
                wheel.getUserState(habbo.getHabboInfo().getId()),
                wheel.getSpinCost(), wheel.getSpinCostType(), wheel.getPrizes()));

        if (prize != null) {
            this.client.sendResponse(new WheelRecentWinsComposer(wheel.getRecentWins(50)));
        }
    }
}
