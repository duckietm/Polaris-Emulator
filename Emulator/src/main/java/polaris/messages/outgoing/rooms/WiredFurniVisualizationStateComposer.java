package polaris.messages.outgoing.rooms;

import java.util.Collection;
import java.util.List;
import polaris.hotel.rooms.WiredHighlight;
import polaris.hotel.rooms.WiredUserTransparency;
import polaris.hotel.rooms.WiredVisualizationEntry;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * Official furni visualization state, sent on entering the room and applied without easing:
 * {@code [n, n x (furni id, transparency, click through, easing), m, m x (furni id, type, strength,
 * color, animation, easing)]}. Furni transparency keeps going out as {@link WiredFurniOpacityComposer},
 * so the transparency list stays empty here.
 */
public class WiredFurniVisualizationStateComposer extends MessageComposer {
    private static final int HEADER = 1826;

    private final List<WiredVisualizationEntry<WiredUserTransparency>> transparency;
    private final List<WiredVisualizationEntry<WiredHighlight>> highlights;

    public WiredFurniVisualizationStateComposer(
            Collection<WiredVisualizationEntry<WiredUserTransparency>> transparency,
            Collection<WiredVisualizationEntry<WiredHighlight>> highlights) {
        this.transparency = WiredVisualizationPackets.entries(transparency);
        this.highlights = WiredVisualizationPackets.entries(highlights);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        WiredVisualizationPackets.appendState(this.response, this.transparency, this.highlights, true);
        return this.response;
    }
}
