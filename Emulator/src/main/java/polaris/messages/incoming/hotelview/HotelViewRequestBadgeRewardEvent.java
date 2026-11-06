package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewBadgeButtonConfigComposer;

public class HotelViewRequestBadgeRewardEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String badge = this.packet.readString();
        this.client.sendResponse(new HotelViewBadgeButtonConfigComposer(
                Emulator.getConfig().getValue("hotelview.badgereward." + badge + ".badge"),
                Emulator.getConfig().getBoolean("hotelview.badgereward." + badge + ".enabled")));
    }
}
