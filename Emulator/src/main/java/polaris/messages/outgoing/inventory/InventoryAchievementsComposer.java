package polaris.messages.outgoing.inventory;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementLevel;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InventoryAchievementsComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InventoryAchievementsComposer);

        synchronized (Emulator.getGameEnvironment().getAchievementManager().getAchievements()) {
            Map<String, Achievement> achievements =
                    Emulator.getGameEnvironment().getAchievementManager().getAchievements();

            this.response.appendInt(achievements.size());
            for (Achievement achievement : achievements.values()) {
                InventoryAchievementsComposer.this.response.appendString(
                        (achievement.name.startsWith("ACH_")
                                ? achievement.name.replace("ACH_", "")
                                : achievement.name));
                Map<Integer, AchievementLevel> levels = achievement.levels();
                InventoryAchievementsComposer.this.response.appendInt(levels.size());

                for (AchievementLevel level : levels.values()) {
                    InventoryAchievementsComposer.this.response.appendInt(level.level);
                    InventoryAchievementsComposer.this.response.appendInt(level.progress);
                }
            }
        }
        return this.response;
    }
}
