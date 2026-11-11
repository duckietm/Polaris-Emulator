package polaris.hotel.rooms;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.generic.alerts.CustomNotificationComposer;

/**
 * The talent show's respect rule (Habbo Stars). In a room with a stage, respect is the audience's vote: it goes only
 * to a user standing on the stage, and only while the room holds an audience. The official client has a notice for
 * each refusal ({@code CustomNotificationComposer} 4 and 5).
 *
 * <p>Off unless {@code hotel.talent.show.enabled} is set: rooms that already use stage furni as decoration keep
 * giving respect as before. {@code hotel.talent.show.stage.furni} names the stage furni ({@code items_base.item_name},
 * separated by {@code ;}) and {@code hotel.talent.show.min.audience} the users a room needs.
 */
public final class TalentShowRespect {
    public static final String ENABLED_KEY = "hotel.talent.show.enabled";
    public static final String STAGE_FURNI_KEY = "hotel.talent.show.stage.furni";
    public static final String MIN_AUDIENCE_KEY = "hotel.talent.show.min.audience";

    public static final String DEFAULT_STAGE_FURNI =
            "hs_circstage;hs_stage_black;hs_stage_blue;hs_stage_pink;hs_stage_long_black;hs_stage_long_blue;hs_stage_long_pink";
    public static final int DEFAULT_MIN_AUDIENCE = 5;

    /** What the rule says about one respect. */
    public enum Verdict {
        ALLOWED(0),
        NOT_ON_STAGE(CustomNotificationComposer.STARS_NOT_CANDIDATE),
        NOT_ENOUGH_USERS(CustomNotificationComposer.STARS_NOT_ENOUGH_USERS);

        private final int notification;

        Verdict(int notification) {
            this.notification = notification;
        }

        /** The {@code CustomNotificationComposer} type that tells the giver, or 0 when the respect goes through. */
        public int notification() {
            return this.notification;
        }
    }

    private TalentShowRespect() {}

    /**
     * The rule itself. A room without a stage is not a talent show; with one, the stage comes first (as the official
     * codes do), then the audience.
     */
    public static Verdict verdict(boolean roomHasStage, boolean targetOnStage, int usersInRoom, int minAudience) {
        if (!roomHasStage) return Verdict.ALLOWED;
        if (!targetOnStage) return Verdict.NOT_ON_STAGE;
        if (usersInRoom < Math.max(1, minAudience)) return Verdict.NOT_ENOUGH_USERS;
        return Verdict.ALLOWED;
    }

    /** The stage furni names of the setting, lower case; blanks are dropped. */
    public static Set<String> stageNames(String setting) {
        if (setting == null) return Set.of();

        return Arrays.stream(setting.split("[;,]"))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Whether {@code giver}'s respect may go to {@code target} in {@code room}, by the hotel's settings. */
    public static Verdict check(Room room, User target) {
        ConfigurationManager configuration = WiredPlatform.configuration();
        if (configuration == null || room == null || target == null) return Verdict.ALLOWED;
        if (!configuration.getBoolean(ENABLED_KEY, false)) return Verdict.ALLOWED;

        Set<String> stages = stageNames(configuration.getValue(STAGE_FURNI_KEY, DEFAULT_STAGE_FURNI));
        if (stages.isEmpty() || !hasStage(room.getFloorItems(), stages)) return Verdict.ALLOWED;

        RoomUnit unit = target.getRoomUnit();
        boolean onStage = unit != null
                && unit.getCurrentLocation() != null
                && hasStage(room.getItemsAt(unit.getCurrentLocation()), stages);

        return verdict(
                true, onStage, room.getUserCount(), configuration.getInt(MIN_AUDIENCE_KEY, DEFAULT_MIN_AUDIENCE));
    }

    private static boolean hasStage(Iterable<UserItem> items, Set<String> stages) {
        if (items == null) return false;

        for (UserItem item : items) {
            if (item == null || item.getBaseItem() == null || item.getBaseItem().getName() == null) continue;
            if (stages.contains(item.getBaseItem().getName().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
