package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.WiredUserTransparency;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;
import java.util.List;

/**
 * Official avatar transparency: {@code [n, n x room index, transparency, click through, easing]}, where
 * a transparency of 100 is invisible.
 */
public class WiredUserTransparencyComposer extends MessageComposer {
    private static final int HEADER = 3664;

    private final List<Integer> roomIndices;
    private final WiredUserTransparency transparency;

    public WiredUserTransparencyComposer(Collection<Integer> roomIndices, WiredUserTransparency transparency) {
        this.roomIndices = WiredVisualizationPackets.ids(roomIndices);
        this.transparency = transparency == null ? WiredUserTransparency.NONE : transparency;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        this.response.appendInt(this.roomIndices.size());
        for (int roomIndex : this.roomIndices) {
            this.response.appendInt(roomIndex);
        }
        this.response.appendInt(this.transparency.transparency());
        this.response.appendBoolean(this.transparency.clickThrough());
        this.response.appendInt(this.transparency.easing());
        return this.response;
    }
}
