package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideSessionErrorComposer;

public class RequestGuideAssistanceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt();
        String message = this.packet.readString();

        GuideTour activeTour =
                Emulator.getGameEnvironment().getGuideManager().getGuideTourByUser(this.client.getUser());

        if (activeTour != null) {
            this.client.sendResponse(new GuideSessionErrorComposer(GuideSessionErrorComposer.SOMETHING_WRONG_REQUEST));
            return;
        }

        GuideTour tour = new GuideTour(this.client.getUser(), message);
        tour.setStartTime(Emulator.getIntUnixTimestamp());

        Emulator.getGameEnvironment().getGuideManager().findHelper(tour);
    }
}
