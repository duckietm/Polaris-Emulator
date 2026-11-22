package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.BuildHeightPolicy;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Tells the client whether the build height widget may be used in the room it just entered, and the
 * range its slider covers. The widget stays hidden until this says it is available, so the underpass
 * switch that lives inside it depends on this packet too.
 */
public class BuildHeightAvailableComposer extends MessageComposer {
    private final boolean available;

    public BuildHeightAvailableComposer(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        return this.available;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BuildHeightAvailableComposer);
        this.response.appendBoolean(this.available);
        this.response.appendInt(BuildHeightPolicy.minHeight());
        this.response.appendInt(BuildHeightPolicy.maxHeight());
        return this.response;
    }
}
