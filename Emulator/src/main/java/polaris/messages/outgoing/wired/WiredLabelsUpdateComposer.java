package polaris.messages.outgoing.wired;

import java.util.List;
import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official furniture labels and regions update (AIR 796): labels added or changed (same entries
 * as {@link WiredLabelsComposer}), then the furni ids of labels the player no longer has.
 */
public class WiredLabelsUpdateComposer extends MessageComposer {
    private final List<WiredExtraLabel> changed;
    private final List<Integer> removedIds;

    public WiredLabelsUpdateComposer(List<WiredExtraLabel> changed, List<Integer> removedIds) {
        this.changed = (changed != null) ? List.copyOf(changed) : List.of();
        this.removedIds = (removedIds != null) ? List.copyOf(removedIds) : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLabelsUpdateComposer);
        this.response.appendInt(this.changed.size());
        for (WiredExtraLabel label : this.changed) {
            this.response.appendInt(WiredLabelsComposer.TYPE_LABEL);
            this.response.appendInt(label.getId());
            this.response.appendString(label.getText());
            this.response.appendInt(label.getColor());
            this.response.appendInt(label.getVisibility());
            this.response.appendBoolean(label.isAlwaysOpen());
        }
        this.response.appendInt(this.removedIds.size());
        for (int removedId : this.removedIds) {
            this.response.appendInt(removedId);
        }
        return this.response;
    }
}
