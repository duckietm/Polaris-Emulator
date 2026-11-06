package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.messages.incoming.MessageHandler;

public class StartSafetyQuizEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readString();

        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("SafetyQuizGraduate"));
    }
}
