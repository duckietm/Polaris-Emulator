package polaris.messages.outgoing.rooms;

import polaris.hotel.bots.dialogue.BotDialogueWindow;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * A bot dialogue in a window of its own: the bot as the client knows it (its negative id), the window's code, the
 * title, what the bot says, the label of the action button, the tabs, the items (label, furni icon, price, detail,
 * tab, most at once) and the footer lines. A code of 0 closes the window.
 */
public class BotDialogueWindowComposer extends MessageComposer {
    private final int botId;
    private final int nonce;
    private final BotDialogueWindow window;

    public BotDialogueWindowComposer(int botId, int nonce, BotDialogueWindow window) {
        this.botId = botId;
        this.nonce = nonce;
        this.window = window;
    }

    public static BotDialogueWindowComposer closed(int botId) {
        return new BotDialogueWindowComposer(botId, 0, new BotDialogueWindow("", "", null, null, null, ""));
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BotDialogueWindowComposer);
        this.response.appendInt(this.botId);
        this.response.appendInt(this.nonce);
        this.response.appendString(this.window.title());
        this.response.appendString(this.window.text());
        this.response.appendString(this.window.action());
        this.response.appendInt(this.window.tabs().size());

        for (String tab : this.window.tabs()) {
            this.response.appendString(tab);
        }

        this.response.appendInt(this.window.items().size());

        for (BotDialogueWindow.Item item : this.window.items()) {
            this.response.appendString(item.label());
            this.response.appendString(item.icon());
            this.response.appendInt((int) Math.min(item.price(), Integer.MAX_VALUE));
            this.response.appendString(item.detail());
            this.response.appendInt(item.tab());
            this.response.appendInt(item.maxAmount());
        }

        this.response.appendInt(this.window.footer().size());

        for (String line : this.window.footer()) {
            this.response.appendString(line);
        }

        return this.response;
    }
}
