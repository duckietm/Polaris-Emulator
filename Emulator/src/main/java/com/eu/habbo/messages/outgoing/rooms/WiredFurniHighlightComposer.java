package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.WiredHighlight;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;
import java.util.List;

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
