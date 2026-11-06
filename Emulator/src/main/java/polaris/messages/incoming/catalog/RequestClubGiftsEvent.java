package polaris.messages.incoming.catalog;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.ClubGiftsComposer;

public class RequestClubGiftsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new ClubGiftsComposer(
                (int) Math.floor(this.client.getUser().getUserStats().getTimeTillNextClubGift() / 86400.0),
                this.client.getUser().getUserStats().getRemainingClubGifts(),
                (int) Math.floor(this.client.getUser().getUserStats().getPastTimeAsClub() / 86400.0)));
    }
}
