package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormLeaveQueueEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        SnowWarManager.getInstance().leaveQueue(this.client.getUser());
    }
}
