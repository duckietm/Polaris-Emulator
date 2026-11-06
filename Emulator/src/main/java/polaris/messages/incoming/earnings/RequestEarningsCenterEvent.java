package polaris.messages.incoming.earnings;

import polaris.hotel.earnings.EarningsCenterManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.earnings.EarningsCenterComposer;

public class RequestEarningsCenterEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        EarningsCenterManager manager = new EarningsCenterManager();
        this.client.sendResponse(new EarningsCenterComposer(manager.getEntries(this.client.getUser())));
    }
}
