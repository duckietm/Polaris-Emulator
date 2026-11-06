package polaris.messages.outgoing.events.mysticbox;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MysticBoxPrizeComposer extends MessageComposer {
    private final String type;
    private final int itemId;

    public MysticBoxPrizeComposer(String type, int itemId) {
        this.type = type;
        this.itemId = itemId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MysticBoxPrizeComposer);
        this.response.appendString(this.type);
        this.response.appendInt(this.itemId);
        return this.response;
    }

    public String getType() {
        return type;
    }

    public int getItemId() {
        return itemId;
    }
}
