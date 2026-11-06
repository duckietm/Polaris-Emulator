package polaris.messages.outgoing.rooms.items.lovelock;

import polaris.hotel.items.interactions.InteractionLoveLock;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class LoveLockFurniStartComposer extends MessageComposer {
    private final InteractionLoveLock loveLock;
    /** {@code true} for the first clicker (owner), {@code false} for the partner — official {@code isOwner} wire. */
    private final boolean isOwner;

    public LoveLockFurniStartComposer(InteractionLoveLock loveLock, boolean isOwner) {
        this.loveLock = loveLock;
        this.isOwner = isOwner;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.LoveLockFurniStartComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.loveLock.getId()));
        this.response.appendBoolean(this.isOwner);
        return this.response;
    }

    public InteractionLoveLock getLoveLock() {
        return loveLock;
    }
}
