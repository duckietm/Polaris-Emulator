package polaris.messages.outgoing.rooms;

import java.util.Collection;
import java.util.List;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.WiredHighlight;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * Official furni highlight: {@code [n, n x furni id, type, strength, color, animation, easing]}, wall
 * items with their id negated. Ours goes out on its own header: the official one is taken here.
 */
public class WiredFurniHighlightComposer extends MessageComposer {
    private static final int HEADER = 9468;

    private final List<Integer> furniIds;
    private final WiredHighlight highlight;

    public WiredFurniHighlightComposer(Collection<Integer> furniIds, WiredHighlight highlight) {
        this.furniIds = WiredVisualizationPackets.ids(furniIds);
        this.highlight = highlight == null ? WiredHighlight.NONE : highlight;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        this.response.appendInt(this.furniIds.size());
        for (int furniId : this.furniIds) {
            this.response.appendInt(BuildersClubItemIds.toWire(furniId));
        }
        WiredVisualizationPackets.appendHighlight(this.response, this.highlight);
        return this.response;
    }
}
