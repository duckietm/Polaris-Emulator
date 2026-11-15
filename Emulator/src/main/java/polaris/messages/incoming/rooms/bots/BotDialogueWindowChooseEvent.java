package polaris.messages.incoming.rooms.bots;

import polaris.hotel.bots.dialogue.BotDialogues;
import polaris.messages.incoming.MessageHandler;

/**
 * A player took an item from a bot's dialogue window: the bot, the code of the window shown, the item's position and
 * how many. It counts only against the window that player was shown and within that item's limit (see
 * {@link BotDialogues}).
 */
public class BotDialogueWindowChooseEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 300;
    }

    @Override
    public void handle() throws Exception {
        int botId = this.packet.readInt();
        int nonce = this.packet.readInt();
        int index = this.packet.readInt();
        int amount = this.packet.readInt();
        BotDialogues.chooseInWindow(this.client.getUser(), botId, nonce, index, amount);
    }
}
