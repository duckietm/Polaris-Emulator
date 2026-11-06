package polaris.messages.outgoing.rooms;

import java.util.Collection;
import java.util.List;
import polaris.hotel.rooms.WiredHighlight;
import polaris.hotel.rooms.WiredUserTransparency;
import polaris.hotel.rooms.WiredVisualizationEntry;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * Official avatar visualization state, sent on entering the room and applied without easing: the
 * layout of {@link WiredFurniVisualizationStateComposer} with room indices instead of furni ids.
 */
public class WiredUserVisualizationStateComposer extends MessageComposer {
    private static final int HEADER = 1454;

    private final List<WiredVisualizationEntry<WiredUserTransparency>> transparency;
    private final List<WiredVisualizationEntry<WiredHighlight>> highlights;

    public WiredUserVisualizationStateComposer(
            Collection<WiredVisualizationEntry<WiredUserTransparency>> transparency,
            Collection<WiredVisualizationEntry<WiredHighlight>> highlights) {
        this.transparency = WiredVisualizationPackets.entries(transparency);
        this.highlights = WiredVisualizationPackets.entries(highlights);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        WiredVisualizationPackets.appendState(this.response, this.transparency, this.highlights, false);
        return this.response;
    }
}
