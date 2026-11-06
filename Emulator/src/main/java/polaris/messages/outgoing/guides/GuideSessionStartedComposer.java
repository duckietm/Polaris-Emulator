package polaris.messages.outgoing.guides;

import polaris.hotel.guides.GuideTour;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideSessionStartedComposer extends MessageComposer {
    private final GuideTour tour;

    public GuideSessionStartedComposer(GuideTour tour) {
        this.tour = tour;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideSessionStartedComposer);
        this.response.appendInt(this.tour.getNoob().getUserInfo().getId());
        this.response.appendString(this.tour.getNoob().getUserInfo().getUsername());
        this.response.appendString(this.tour.getNoob().getUserInfo().getLook());
        this.response.appendInt(this.tour.getHelper().getUserInfo().getId());
        this.response.appendString(this.tour.getHelper().getUserInfo().getUsername());
        this.response.appendString(this.tour.getHelper().getUserInfo().getLook());
        return this.response;
    }

    public GuideTour getTour() {
        return tour;
    }
}
