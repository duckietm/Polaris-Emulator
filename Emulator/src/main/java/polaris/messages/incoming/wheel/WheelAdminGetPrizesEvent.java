package polaris.messages.incoming.wheel;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wheel.WheelAdminPrizesComposer;

public class WheelAdminGetPrizesEvent extends MessageHandler {
    public static final String PERMISSION_KEY = "acc_wheeladmin";

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null || !this.client.getUser().hasPermission(PERMISSION_KEY)) {
            return;
        }

        this.client.sendResponse(new WheelAdminPrizesComposer(
                Emulator.getGameEnvironment().getWheelManager().getPrizes()));
    }
}
