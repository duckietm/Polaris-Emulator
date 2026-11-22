package polaris.messages.incoming.rooms.bots;

import polaris.hotel.bots.dialogue.BotDialogues;
import polaris.messages.incoming.MessageHandler;

/**
 * A player picked an option in a bot's menu: the bot, the code of the page shown and the option's position. It counts
 * only against the page that player was shown (see {@link BotDialogues}).
 */
public class BotDialogueChooseEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 300;
    }

    @Override
    public void handle() throws Exception {
        int botId = this.packet.readInt();
        int nonce = this.packet.readInt();
        int index = this.packet.readInt();
        BotDialogues.choose(this.client.getUser(), botId, nonce, index);
    }
}
