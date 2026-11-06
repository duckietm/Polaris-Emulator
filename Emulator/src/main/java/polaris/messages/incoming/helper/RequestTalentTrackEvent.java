package polaris.messages.incoming.helper;

import java.util.Locale;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.achievements.TalentTrackType;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.achievements.talenttrack.TalentTrackComposer;

public class RequestTalentTrackEvent extends MessageHandler {
    /** The track a client named, or null for a name that is not one: valueOf threw and the request ended in an error. */
    static TalentTrackType parseType(String name) {
        if (name == null) return null;

        try {
            return TalentTrackType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    // Each request works the level out again from the achievements: one a second is plenty for opening the window.
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (Emulator.getConfig().getBoolean("hotel.talenttrack.enabled")) {
            TalentTrackType type = parseType(this.packet.readString());
            if (type == null) return;
            GameEnvironment environment = WiredPlatform.gameEnvironment();
            AchievementManager manager = environment == null ? null : environment.getAchievementManager();

            // The level is otherwise only worked out when an achievement progresses: catch up before showing it.
            if (manager != null && manager.getTalenTrackLevels(type) != null) {
                manager.handleTalentTrackAchievement(this.client.getUser(), type, null);
            }

            this.client.sendResponse(new TalentTrackComposer(this.client.getUser(), type));
        }
    }
}
