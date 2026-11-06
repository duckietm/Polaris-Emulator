package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.messages.incoming.MessageHandler;

public class GuideCloseHelpRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByUser(this.client.getUser());

        if (tour != null) {
            Emulator.getGameEnvironment().getGuideManager().endSession(tour);
        }
    }
}
