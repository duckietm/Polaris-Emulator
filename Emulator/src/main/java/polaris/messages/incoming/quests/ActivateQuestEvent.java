package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** ActivateQuest (793): accept a quest from the hotel view or the daily widget. */
public class ActivateQuestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int questId = this.packet.readInt();
        Emulator.getGameEnvironment().getQuestManager().accept(this.client.getUser(), questId);
    }
}
