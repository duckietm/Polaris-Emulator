package com.eu.habbo.messages.incoming.wheel;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wheel.WheelAdminPrizesComposer;

public class WheelAdminGetPrizesEvent extends MessageHandler {
    public static final String PERMISSION_KEY = "acc_wheeladmin";

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || !this.client.getHabbo().hasPermission(PERMISSION_KEY)) {
            return;
        }

        this.client.sendResponse(new WheelAdminPrizesComposer(
                Emulator.getGameEnvironment().getWheelManager().getPrizes()));
    }
}
