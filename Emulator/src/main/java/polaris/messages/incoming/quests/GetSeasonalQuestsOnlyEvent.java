package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** GetSeasonalQuestsOnly (1190): the seasonal calendar asks for the seasonal campaigns. */
public class GetSeasonalQuestsOnlyEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Emulator.getGameEnvironment().getQuestManager().sendSeasonalQuests(this.client.getUser());
    }
}
