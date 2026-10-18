package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
