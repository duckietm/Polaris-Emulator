package polaris.messages.outgoing.rooms.items;

import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PostItDataComposer extends MessageComposer {
    private final InteractionPostIt postIt;

    public PostItDataComposer(InteractionPostIt postIt) {
        this.postIt = postIt;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.postIt.getExtradata().isEmpty() || this.postIt.getExtradata().length() < 6) {
            this.postIt.setExtradata("FFFF33");
        }

        this.response.init(Outgoing.PostItDataComposer);
        this.response.appendString(BuildersClubItemIds.toWire(this.postIt.getId()) + "");
        this.response.appendString(this.postIt.getExtradata());
        return this.response;
    }

    public InteractionPostIt getPostIt() {
        return postIt;
    }
}
