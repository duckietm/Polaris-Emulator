package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.guides.GuideTour;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideSessionErrorComposer;

public class RequestGuideAssistanceEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        this.packet.readInt();
        // The request text reaches a helper's screen like a guide message, so it gets the same length cap and word
        // filter; it had neither, and no pace either.
        String message = GuideUserMessageEvent.normalize(this.packet.readString());

        GameEnvironment environment = Emulator.getGameEnvironment();
        GuideTour activeTour = environment.getGuideManager().getGuideTourByUser(this.client.getUser());

        if (activeTour != null) {
            this.client.sendResponse(new GuideSessionErrorComposer(GuideSessionErrorComposer.SOMETHING_WRONG_REQUEST));
            return;
        }

        if (!message.isEmpty()) {
            message = environment.getWordFilter().filter(message, this.client.getUser());
        }

        GuideTour tour = new GuideTour(this.client.getUser(), message);
        tour.setStartTime(Emulator.getIntUnixTimestamp());

        environment.getGuideManager().findHelper(tour);
    }
}
