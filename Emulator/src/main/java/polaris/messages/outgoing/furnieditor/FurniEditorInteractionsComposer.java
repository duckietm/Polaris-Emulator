package polaris.messages.outgoing.furnieditor;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FurniEditorInteractionsComposer extends MessageComposer {
    private final List<String> interactions;

    public FurniEditorInteractionsComposer(List<String> interactions) {
        this.interactions = interactions;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniEditorInteractionsComposer);
        this.response.appendInt(this.interactions.size());

        for (String interaction : this.interactions) {
            this.response.appendString(interaction);
        }

        return this.response;
    }
}
