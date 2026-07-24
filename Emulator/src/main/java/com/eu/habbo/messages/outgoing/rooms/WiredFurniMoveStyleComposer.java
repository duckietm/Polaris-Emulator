package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;

/**
 * Capability-gated hint that upcoming wired movements for these items should animate with a
 * non-linear style. Sent only to clients that announced {@code WIRED_FEATURE_MOVE_STYLE}; the
 * legacy {@link WiredMovementsComposer} packet is never altered, so unaware clients simply keep
 * the linear animation.
 */
public class WiredFurniMoveStyleComposer extends MessageComposer {
    private static final int HEADER = 5110;
    private static final int MAXIMUM_ITEMS = 1_000;

    private final Collection<Integer> itemIds;
    private final int style;
    private final int intensity;

    public WiredFurniMoveStyleComposer(Collection<Integer> itemIds, int style, int intensity) {
        this.itemIds = itemIds;
        this.style = Math.max(0, Math.min(6, style));
        this.intensity = Math.max(0, Math.min(100, intensity));
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        int count = Math.min(this.itemIds.size(), MAXIMUM_ITEMS);
        this.response.appendInt(count);
        int written = 0;
        for (Integer itemId : this.itemIds) {
            if (written++ >= count) {
                break;
            }
            this.response.appendInt(itemId);
        }
        this.response.appendInt(this.style);
        this.response.appendInt(this.intensity);
        return this.response;
    }
}
