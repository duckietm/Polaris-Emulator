package polaris.messages.incoming.rooms.bots;

import polaris.hotel.bots.dialogue.BotDialogues;
import polaris.messages.incoming.MessageHandler;

/** A player opened a bot's menu: the bot's dialogue, if it has one, answers with its first page. */
public class BotDialogueOpenEvent extends MessageHandler {
    // The menu opens on a click; this keeps a script from asking for pages in a loop.
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int botId = this.packet.readInt();
        BotDialogues.open(this.client.getUser(), botId);
    }
}
