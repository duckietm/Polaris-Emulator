package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BotForceOpenContextMenuComposer extends MessageComposer {
    private final int botId;

    public BotForceOpenContextMenuComposer(int botId) {
        this.botId = botId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BotForceOpenContextMenuComposer);
        this.response.appendInt(this.botId);
        return this.response;
    }

    public int getBotId() {
        return botId;
    }
}
