package polaris.messages.incoming.hotelview;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.BonusRareComposer;

public class HotelViewRequestBonusRareEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new BonusRareComposer(this.client.getUser()));
    }
}
