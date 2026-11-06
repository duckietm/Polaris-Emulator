package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideSessionRequesterRoomComposer;

public class GuideVisitUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByHelper(this.client.getUser());

        if (tour != null) {
            this.client.sendResponse(new GuideSessionRequesterRoomComposer(
                    tour.getNoob().getUserInfo().getCurrentRoom()));
        }
    }
}
