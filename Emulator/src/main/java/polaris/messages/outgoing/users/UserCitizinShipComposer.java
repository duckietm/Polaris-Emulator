package polaris.messages.outgoing.users;

import java.util.LinkedHashMap;
import polaris.Emulator;
import polaris.hotel.achievements.TalentTrackLevel;
import polaris.hotel.achievements.TalentTrackType;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official {@code TalentTrackLevelMessageEvent} (1203): {@code (talentTrackName, level,
 * maxLevel)}. {@code TalentPromoCtrl} needs the real maxLevel to decide whether the hotel view
 * still promotes the track, so the pair must reflect the user's actual progress.
 */
public class UserCitizinShipComposer extends MessageComposer {
    private static final int DEFAULT_LEVEL = 4;

    private final String name;
    private final int level;
    private final int maxLevel;

    /**
     * @deprecated kept for the plugin ABI; use {@link #UserCitizinShipComposer(String, int, int)}
     *     or {@link #forUser(Habbo, TalentTrackType)} so the client sees the real levels.
     */
    @Deprecated
    public UserCitizinShipComposer(String name) {
        this(name, DEFAULT_LEVEL, DEFAULT_LEVEL);
    }

    public UserCitizinShipComposer(String name, int level, int maxLevel) {
        this.name = name;
        this.level = level;
        this.maxLevel = maxLevel;
    }

    /** Builds the composer from the user's progress on {@code type}. */
    public static UserCitizinShipComposer forUser(User user, TalentTrackType type) {
        return new UserCitizinShipComposer(
                type.name().toLowerCase(), user.getUserStats().talentTrackLevel(type), maxLevelOf(type));
    }

    private static int maxLevelOf(TalentTrackType type) {
        LinkedHashMap<Integer, TalentTrackLevel> levels =
                Emulator.getGameEnvironment().getAchievementManager().getTalenTrackLevels(type);

        if (levels == null || levels.isEmpty()) {
            return DEFAULT_LEVEL;
        }

        int max = 0;

        for (int level : levels.keySet()) {
            max = Math.max(max, level);
        }

        return max;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserCitizinShipComposer);
        this.response.appendString(this.name);
        this.response.appendInt(this.level);
        this.response.appendInt(this.maxLevel);
        return this.response;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
