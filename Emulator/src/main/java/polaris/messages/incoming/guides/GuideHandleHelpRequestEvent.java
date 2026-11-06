package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.messages.incoming.MessageHandler;

public class GuideHandleHelpRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByHelper(this.client.getUser());

        if (tour == null) {
            return;
        }

        boolean accepted = this.packet.readBoolean();

        if (!accepted) {
            Emulator.getGameEnvironment().getGuideManager().declineTour(tour);
        } else {
            Emulator.getGameEnvironment().getGuideManager().startSession(tour, this.client.getUser());
        }
    }
}
