package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.messages.incoming.MessageHandler;

public class GuideRecommendHelperEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean recommend = this.packet.readBoolean();

        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByNoob(this.client.getUser());

        if (tour != null) {
            Emulator.getGameEnvironment().getGuideManager().recommend(tour, recommend);
        }
    }
}
