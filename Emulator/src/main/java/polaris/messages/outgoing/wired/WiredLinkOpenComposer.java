package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * WIRED Link open (AIR 1715, ours {@link Outgoing#WiredLinkOpenComposer}): the owner used their Link;
 * the client answers with a configuration request for it.
 */
public class WiredLinkOpenComposer extends MessageComposer {
    private final int furniId;

    public WiredLinkOpenComposer(int furniId) {
        this.furniId = furniId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLinkOpenComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.furniId));
        return this.response;
    }
}
