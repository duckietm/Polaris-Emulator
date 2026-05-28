package com.eu.habbo.messages.incoming.wheel;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.wheel.WheelManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wheel.WheelAdminPrizesComposer;
import com.eu.habbo.messages.outgoing.wheel.WheelDataComposer;

public class WheelAdminSavePrizesEvent extends MessageHandler {
    public static final String PERMISSION_KEY = "acc_wheeladmin";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || !this.client.getHabbo().hasPermission(PERMISSION_KEY)) {
            return;
        }

        WheelManager wheel = Emulator.getGameEnvironment().getWheelManager();

        int count = this.packet.readInt();
        if (count <= 0 || count > WheelManager.MAX_PRIZES_PER_SAVE) return;

        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
            String type = this.packet.readString();
            String value = this.packet.readString();
            int amount = this.packet.readInt();
            int pointsType = this.packet.readInt();
            int weight = this.packet.readInt();
            String label = this.packet.readString();
            wheel.savePrize(id, type, value, amount, pointsType, weight, label);
        }

        wheel.reload();

        this.client.sendResponse(new WheelAdminPrizesComposer(wheel.getPrizes()));
        this.client.sendResponse(new WheelDataComposer(
                wheel.getUserState(this.client.getHabbo().getHabboInfo().getId()),
                wheel.getSpinCost(), wheel.getSpinCostType(), wheel.getPrizes()));
    }
}
