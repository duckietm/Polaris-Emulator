package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** AcceptQuest (3604): accept a quest from inside a room. */
public class AcceptQuestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int questId = this.packet.readInt();
        Emulator.getGameEnvironment().getQuestManager().accept(this.client.getUser(), questId);
    }
}
