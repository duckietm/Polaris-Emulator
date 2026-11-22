package polaris.messages.outgoing.rooms;

import polaris.hotel.bots.dialogue.BotDialoguePage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * A page of a bot dialogue for the bot's menu: the bot as the client knows it (its negative id), the page's code, the
 * text and the option labels. A code of 0 with no options closes the dialogue in the menu.
 */
public class BotDialogueComposer extends MessageComposer {
    private final int botId;
    private final int nonce;
    private final BotDialoguePage page;

    public BotDialogueComposer(int botId, int nonce, BotDialoguePage page) {
        this.botId = botId;
        this.nonce = nonce;
        this.page = page;
    }

    public static BotDialogueComposer closed(int botId) {
        return new BotDialogueComposer(botId, 0, new BotDialoguePage("", null));
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BotDialogueComposer);
        this.response.appendInt(this.botId);
        this.response.appendInt(this.nonce);
        this.response.appendString(this.page.text());
        this.response.appendInt(this.page.options().size());

        for (BotDialoguePage.Option option : this.page.options()) {
            this.response.appendString(option.label());
        }

        return this.response;
    }
}
