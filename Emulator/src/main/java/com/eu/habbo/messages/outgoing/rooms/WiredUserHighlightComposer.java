package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.WiredHighlight;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;
import java.util.List;

/** Official avatar highlight: {@code [n, n x room index, type, strength, color, animation, easing]}. */
public class WiredUserHighlightComposer extends MessageComposer {
    private static final int HEADER = 1684;

    private final List<Integer> roomIndices;
    private final WiredHighlight highlight;

    public WiredUserHighlightComposer(Collection<Integer> roomIndices, WiredHighlight highlight) {
        this.roomIndices = WiredVisualizationPackets.ids(roomIndices);
        this.highlight = highlight == null ? WiredHighlight.NONE : highlight;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        this.response.appendInt(this.roomIndices.size());
        for (int roomIndex : this.roomIndices) {
            this.response.appendInt(roomIndex);
        }
        WiredVisualizationPackets.appendHighlight(this.response, this.highlight);
        return this.response;
    }
}
