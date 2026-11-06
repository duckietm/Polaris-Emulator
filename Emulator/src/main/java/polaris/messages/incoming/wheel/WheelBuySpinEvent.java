package polaris.messages.incoming.wheel;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.wheel.WheelManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wheel.WheelDataComposer;

public class WheelBuySpinEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        WheelManager wheel = Emulator.getGameEnvironment().getWheelManager();
        if (!wheel.isEnabled()) return;

        wheel.buySpin(user); // whether or not it succeeds, resend the balance

        this.client.sendResponse(new WheelDataComposer(
                wheel.getUserState(user.getUserInfo().getId()),
                wheel.getSpinCost(),
                wheel.getSpinCostType(),
                wheel.getPrizes()));
    }
}
