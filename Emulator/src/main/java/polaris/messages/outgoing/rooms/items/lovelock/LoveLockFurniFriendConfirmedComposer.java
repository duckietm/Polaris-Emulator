package polaris.messages.outgoing.rooms.items.lovelock;

import polaris.hotel.items.interactions.InteractionLoveLock;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class LoveLockFurniFriendConfirmedComposer extends MessageComposer {
    private final InteractionLoveLock loveLock;

    public LoveLockFurniFriendConfirmedComposer(InteractionLoveLock loveLock) {
        this.loveLock = loveLock;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.LoveLockFurniFriendConfirmedComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.loveLock.getId()));
        return this.response;
    }

    public InteractionLoveLock getLoveLock() {
        return loveLock;
    }
}
