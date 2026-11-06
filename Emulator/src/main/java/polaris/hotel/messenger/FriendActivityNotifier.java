package polaris.hotel.messenger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.users.User;
import polaris.hotel.users.UserStats;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.friends.FriendNotificationComposer;

/**
 * The official friend bar activity ({@code FriendToolbarNotification}, AIR {@code HabboFriendBarData.makeNotification}
 * and the friend entity tokens): a friend's tab on the bar shows an icon and, when opened, a line for a room event
 * the friend started, an achievement level they unlocked, a quest they completed, or the game they are playing.
 *
 * <p>Only online friends are told, and only friends who still have the actor on their own list. Nothing goes out for
 * an actor who hides their online status, and a friend who ignores or blocks the actor is skipped. Each kind can be
 * switched off with its {@code hotel.friendbar.activity.*} setting.
 */
public final class FriendActivityNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendActivityNotifier.class);

    public static final String ENABLED = "hotel.friendbar.activity.enabled";
    public static final String ROOM_EVENTS = "hotel.friendbar.activity.room_events";
    public static final String ACHIEVEMENTS = "hotel.friendbar.activity.achievements";
    public static final String QUESTS = "hotel.friendbar.activity.quests";
    public static final String GAMES = "hotel.friendbar.activity.games";

    /** The game name the client reads as {@code gamecenter.<name>.name} (SnowStorm). */
    public static final String SNOWSTORM = "snowwar";

    private static final IntFunction<User> HOTEL_ONLINE = id -> {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        return environment == null ? null : environment.getUserManager().getUser(id);
    };
    private static final BiPredicate<String, Boolean> HOTEL_SETTINGS = (key, fallback) -> {
        ConfigurationManager config = WiredPlatform.configuration();
        return config == null ? fallback : config.getBoolean(key, fallback);
    };

    private static final AtomicReference<IntFunction<User>> ONLINE = new AtomicReference<>(HOTEL_ONLINE);
    private static final AtomicReference<BiPredicate<String, Boolean>> SETTINGS = new AtomicReference<>(HOTEL_SETTINGS);

    private FriendActivityNotifier() {}

    /** Swap the online lookup and the settings (tests). Null puts the hotel's back. */
    public static void use(IntFunction<User> online, BiPredicate<String, Boolean> settings) {
        ONLINE.set(online == null ? HOTEL_ONLINE : online);
        SETTINGS.set(settings == null ? HOTEL_SETTINGS : settings);
    }

    /** A room event (promotion) started by the actor: AIR {@code RoomEventToken} shows its title. */
    public static int roomEventStarted(User actor, String title) {
        return notifyFriends(actor, FriendNotificationComposer.ROOM_EVENT, ROOM_EVENTS, title);
    }

    /** An achievement level: AIR {@code AchievementToken} shows the name of this badge code. */
    public static int achievementUnlocked(User actor, String badgeCode) {
        return notifyFriends(actor, FriendNotificationComposer.ACHIEVEMENT_COMPLETED, ACHIEVEMENTS, badgeCode);
    }

    /** A completed quest: AIR {@code QuestToken} shows {@code ${quests.<campaign>.<code>.name}}. */
    public static int questCompleted(User actor, String campaignCode, String questCode) {
        if (campaignCode == null || campaignCode.isEmpty() || questCode == null || questCode.isEmpty()) return 0;
        return notifyFriends(actor, FriendNotificationComposer.QUEST_COMPLETED, QUESTS, campaignCode + "." + questCode);
    }

    /** The actor entered a game: AIR {@code GameToken} stays on the tab until the game finishes. */
    public static int playingGame(User actor, String gameName) {
        return notifyFriends(actor, FriendNotificationComposer.IS_PLAYING_GAME, GAMES, gameName);
    }

    /** The actor's game is over: removes the playing token. */
    public static int finishedGame(User actor, String gameName) {
        return notifyFriends(actor, FriendNotificationComposer.FINISHED_GAME, GAMES, gameName);
    }

    static int notifyFriends(User actor, int type, String settingKey, String data) {
        if (data == null || data.isEmpty()) return 0;

        try {
            BiPredicate<String, Boolean> settings = SETTINGS.get();
            if (!settings.test(ENABLED, true) || !settings.test(settingKey, true)) return 0;

            List<User> recipients = recipients(actor, ONLINE.get());
            if (recipients.isEmpty()) return 0;

            ServerMessage message =
                    new FriendNotificationComposer(actor.getUserInfo().getId(), type, data).compose();
            for (User friend : recipients) friend.getClient().sendResponse(message);
            return recipients.size();
        } catch (RuntimeException e) {
            LOGGER.debug("Friend bar activity not sent", e);
            return 0;
        }
    }

    /** The online friends that see the actor's activity. */
    static List<User> recipients(User actor, IntFunction<User> online) {
        List<User> recipients = new ArrayList<>();
        if (actor == null || actor.getUserInfo() == null || actor.getMessenger() == null) return recipients;

        UserStats actorStats = actor.getUserStats();
        if (actorStats != null && actorStats.hideOnline) return recipients;

        int actorId = actor.getUserInfo().getId();
        for (MessengerBuddy buddy : actor.getMessenger().getFriends().values()) {
            if (buddy == null || buddy.getId() == actorId) continue;

            User friend = online.apply(buddy.getId());
            if (friend == null || friend == actor || friend.getClient() == null) continue;
            if (friend.getMessenger() == null || friend.getMessenger().getFriend(actorId) == null) continue;

            UserStats friendStats = friend.getUserStats();
            if (friendStats != null && (friendStats.userIgnored(actorId) || friendStats.userBlocked(actorId))) continue;

            recipients.add(friend);
        }
        return recipients;
    }
}
