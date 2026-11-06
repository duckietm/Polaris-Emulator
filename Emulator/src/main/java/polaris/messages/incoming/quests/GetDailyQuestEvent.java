package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** GetDailyQuest (2486): the daily widget asks for the index-th open quest of a difficulty. */
public class GetDailyQuestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean easy = this.packet.readBoolean();
        int index = this.packet.readInt();
        Emulator.getGameEnvironment().getQuestManager().sendDailyQuest(this.client.getUser(), easy, index);
    }
}
