package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideSessionPartnerIsTypingComposer;

public class GuideUserTypingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean typing = this.packet.readBoolean();

        GuideTour tour =
                Emulator.getGameEnvironment().getGuideManager().getActiveGuideTourByUser(this.client.getUser());

        if (tour == null) return;

        User partner = tour.getHelper() == this.client.getUser() ? tour.getNoob() : tour.getHelper();

        if (partner != null && partner.getClient() != null) {
            partner.getClient().sendResponse(new GuideSessionPartnerIsTypingComposer(typing));
        }
    }
}
