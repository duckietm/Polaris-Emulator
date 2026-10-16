package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.WiredHighlight;
import com.eu.habbo.habbohotel.rooms.WiredUserTransparency;
import com.eu.habbo.habbohotel.rooms.WiredVisualizationEntry;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;
import java.util.List;

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
        WiredVisualizationPackets.appendState(this.response, this.transparency, this.highlights);
        return this.response;
    }
}
