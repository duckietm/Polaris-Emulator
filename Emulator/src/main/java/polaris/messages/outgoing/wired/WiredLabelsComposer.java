package polaris.messages.outgoing.wired;

import java.util.List;
import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official furniture labels and regions (AIR 2237): every label this player may see; the client
 * replaces what it had. Each entry is a type (0 = label), the furni id and the text, then the
 * label's colour, visibility and whether it stays open.
 */
public class WiredLabelsComposer extends MessageComposer {
    public static final int TYPE_LABEL = 0;

    private final List<WiredExtraLabel> labels;

    public WiredLabelsComposer(List<WiredExtraLabel> labels) {
        this.labels = (labels != null) ? List.copyOf(labels) : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLabelsComposer);
        this.response.appendInt(this.labels.size());
        for (WiredExtraLabel label : this.labels) {
            this.response.appendInt(TYPE_LABEL);
            this.response.appendInt(label.getId());
            this.response.appendString(label.getText());
            this.response.appendInt(label.getColor());
            this.response.appendInt(label.getVisibility());
            this.response.appendBoolean(label.isAlwaysOpen());
        }
        return this.response;
    }
}
