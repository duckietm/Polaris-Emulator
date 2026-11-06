package polaris.messages.outgoing.inventory;

import polaris.hotel.bots.Bot;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AddBotComposer extends MessageComposer {
    private final Bot bot;

    public AddBotComposer(Bot bot) {
        this.bot = bot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AddBotComposer);
        this.response.appendInt(this.bot.getId());
        this.response.appendString(this.bot.getName());
        this.response.appendString(this.bot.getMotto());
        this.response.appendString(this.bot.getGender().toString().toLowerCase().charAt(0) + "");
        this.response.appendString(this.bot.getFigure());
        this.response.appendBoolean(true);
        return this.response;
    }

    public Bot getBot() {
        return bot;
    }
}
