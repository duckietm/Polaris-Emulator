package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HallOfFameComposer;
import polaris.messages.outgoing.hotelview.HotelViewDataComposer;
import polaris.messages.outgoing.hotelview.NewsListComposer;

public class RequestNewsListEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new HotelViewDataComposer("2013-05-08 13:0", "gamesmaker"));
        this.client.sendResponse(new HallOfFameComposer(
                Emulator.getGameEnvironment().getHotelViewManager().getHallOfFame()));
        this.client.sendResponse(new NewsListComposer());
    }
}
