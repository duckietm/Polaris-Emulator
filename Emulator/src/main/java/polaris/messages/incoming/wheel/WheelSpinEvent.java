package polaris.messages.incoming.wheel;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.wheel.WheelManager;
import polaris.hotel.wheel.WheelPrize;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wheel.WheelDataComposer;
import polaris.messages.outgoing.wheel.WheelRecentWinsComposer;
import polaris.messages.outgoing.wheel.WheelResultComposer;

public class WheelSpinEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        WheelManager wheel = Emulator.getGameEnvironment().getWheelManager();
        if (!wheel.isEnabled()) return;

        WheelPrize prize = wheel.spin(user);

        if (prize != null) {
            this.client.sendResponse(new WheelResultComposer(prize.id));
        }

        // Refresh the balance either way so the client unlocks the wheel.
        this.client.sendResponse(new WheelDataComposer(
                wheel.getUserState(user.getUserInfo().getId()),
                wheel.getSpinCost(),
                wheel.getSpinCostType(),
                wheel.getPrizes()));

        if (prize != null) {
            this.client.sendResponse(new WheelRecentWinsComposer(wheel.getRecentWins(50)));
        }
    }
}
