package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.wired.core.WiredMoveStyleHelper;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;

/**
 * Capability-gated hint that upcoming wired movements for these objects should animate with a
 * non-linear style. Sent only to clients that announced {@code WIRED_FEATURE_MOVE_STYLE}; the
 * legacy {@link WiredMovementsComposer} packet is never altered, so unaware clients simply keep
 * the linear animation.
 *
 * <p>Wire: {@code int count, int[count] ids, int style, int intensity, int overshootTiles, int
 * kind}. The last two trail the original layout, so a client that stops after the intensity reads
 * it as before. A kind of {@link WiredMoveStyleHelper#KIND_UNIT} names room units, not furni, and
 * only goes to clients that announced {@code WIRED_FEATURE_TRAJECTORY}.
 */
public class WiredFurniMoveStyleComposer extends MessageComposer {
    private static final int HEADER = 5110;
    private static final int MAXIMUM_ITEMS = 1_000;
    public static final int OVERSHOOT_MIN = -64;
    public static final int OVERSHOOT_MAX = 64;

    private final Collection<Integer> itemIds;
    private final int style;
    private final int intensity;
    private final int overshoot;
    private final int kind;

    public WiredFurniMoveStyleComposer(Collection<Integer> itemIds, int style, int intensity) {
        this(itemIds, style, intensity, 0, WiredMoveStyleHelper.KIND_FURNI);
    }

    public WiredFurniMoveStyleComposer(Collection<Integer> itemIds, int style, int intensity, int overshoot, int kind) {
        this.itemIds = itemIds;
        this.style = Math.max(0, Math.min(WiredMoveStyleHelper.STYLE_JUMP, style));
        // An easing style's intensity is a percentage; a jump's is its signed strength.
        this.intensity = (this.style == WiredMoveStyleHelper.STYLE_JUMP)
                ? Math.max(-1000, Math.min(1000, intensity))
                : Math.max(0, Math.min(100, intensity));
        this.overshoot = Math.max(OVERSHOOT_MIN, Math.min(OVERSHOOT_MAX, overshoot));
        this.kind = (kind == WiredMoveStyleHelper.KIND_UNIT)
                ? WiredMoveStyleHelper.KIND_UNIT
                : WiredMoveStyleHelper.KIND_FURNI;
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
            this.response.appendInt(BuildersClubItemIds.toWire(itemId));
        }
        this.response.appendInt(this.style);
        this.response.appendInt(this.intensity);
        this.response.appendInt(this.overshoot);
        this.response.appendInt(this.kind);
        return this.response;
    }
}
