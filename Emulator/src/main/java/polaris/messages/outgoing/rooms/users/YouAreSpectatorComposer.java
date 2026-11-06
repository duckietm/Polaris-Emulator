package polaris.messages.outgoing.rooms.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** YouAreSpectator (AIR {@code ?_-bt?/?_-PI?}): the room id the user watches as a spectator. */
public class YouAreSpectatorComposer extends MessageComposer {
    private final int roomId;

    /** Kept for plugins; sends room id 0. */
    public YouAreSpectatorComposer() {
        this(0);
    }

    public YouAreSpectatorComposer(int roomId) {
        this.roomId = roomId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YouAreSpectatorComposer);
        this.response.appendInt(this.roomId);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }
}
