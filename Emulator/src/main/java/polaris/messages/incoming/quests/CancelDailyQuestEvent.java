package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** CancelDailyQuest (3133): the daily widget cancels the tracked daily quest. */
public class CancelDailyQuestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Emulator.getGameEnvironment().getQuestManager().cancelDaily(this.client.getUser());
    }
}
