package polaris.messages.outgoing.inventory;

import polaris.hotel.bots.Bot;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RemoveBotComposer extends MessageComposer {
    private final Bot bot;

    public RemoveBotComposer(Bot bot) {
        this.bot = bot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveBotComposer);
        this.response.appendInt(this.bot.getId());
        return this.response;
    }

    public Bot getBot() {
        return bot;
    }
}
