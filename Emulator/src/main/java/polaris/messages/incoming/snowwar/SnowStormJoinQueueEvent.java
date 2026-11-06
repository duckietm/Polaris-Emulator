package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormJoinQueueEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        if (!SnowWarManager.getInstance()
                .allowPacket(this.client.getUser().getUserInfo().getId())) {
            return;
        }

        SnowWarManager.getInstance().joinQueue(this.client.getUser());
    }
}
