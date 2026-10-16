package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.WiredHighlight;
import com.eu.habbo.habbohotel.rooms.WiredUserTransparency;
import com.eu.habbo.habbohotel.rooms.WiredVisualizationEntry;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;
import java.util.List;

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
        WiredVisualizationPackets.appendState(this.response, this.transparency, this.highlights);
        return this.response;
    }
}
