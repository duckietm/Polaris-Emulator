package polaris.messages.outgoing.rooms.items.rentablespaces;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** RentableSpaceRentFailed (1868): the {@code reason} is one of the RentableSpaceInfoComposer codes. */
public class RentableSpaceRentFailedComposer extends MessageComposer {
    private final int reason;

    public RentableSpaceRentFailedComposer(int reason) {
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RentableSpaceRentFailedComposer);
        this.response.appendInt(this.reason);
        return this.response;
    }

    public int getReason() {
        return reason;
    }
}
