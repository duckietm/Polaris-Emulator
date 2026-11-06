package polaris.messages.incoming.achievements;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.achievements.AchievementListComposer;

public class RequestAchievementsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new AchievementListComposer(this.client.getUser()));
    }
}
