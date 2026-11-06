package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.messages.incoming.MessageHandler;

public class GuideCancelHelpRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByNoob(this.client.getUser());

        if (tour != null) {
            tour.end();
            Emulator.getGameEnvironment().getGuideManager().endSession(tour);
        }
    }
}
