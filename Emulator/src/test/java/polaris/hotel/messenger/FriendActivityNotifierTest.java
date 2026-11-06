package polaris.hotel.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.messages.ServerMessage;

/** AIR {@code HabboFriendBarData.makeNotification}: online friends see what a friend does on their friend bar. */
class FriendActivityNotifierTest {
    private final Map<Integer, User> online = new HashMap<>();
    private final Map<String, Boolean> settings = new HashMap<>();

    private User user(int id, UserStats stats) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getUserStats()).thenReturn(stats);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        Messenger messenger = mock(Messenger.class);
        when(messenger.getFriends()).thenReturn(new ConcurrentHashMap<>());
        when(user.getMessenger()).thenReturn(messenger);
        this.online.put(id, user);
        return user;
    }

    private static void befriend(User one, User other) {
        int otherId = other.getUserInfo().getId();
        MessengerBuddy buddy = mock(MessengerBuddy.class);
        when(buddy.getId()).thenReturn(otherId);
        one.getMessenger().getFriends().put(otherId, buddy);
        when(one.getMessenger().getFriend(otherId)).thenReturn(buddy);
    }

    private void wire() {
        FriendActivityNotifier.use(this.online::get, (key, fallback) -> this.settings.getOrDefault(key, fallback));
    }

    @AfterEach
    void restore() {
        FriendActivityNotifier.use(null, null);
    }

    @Test
    void onlineFriendsGetTheAirPacket() {
        User actor = this.user(7, mock(UserStats.class));
        User friend = this.user(8, mock(UserStats.class));
        befriend(actor, friend);
        befriend(friend, actor);
        this.wire();

        assertEquals(1, FriendActivityNotifier.achievementUnlocked(actor, "ACH_Login3"));

        ArgumentCaptor<ServerMessage> sent = ArgumentCaptor.forClass(ServerMessage.class);
        verify(friend.getClient()).sendResponse(sent.capture());
        ByteBuf packet = sent.getValue().get();
        packet.skipBytes(6);
        assertEquals("7", readString(packet));
        assertEquals(1, packet.readInt());
        assertEquals("ACH_Login3", readString(packet));
        verify(actor.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    @Test
    void privacyAndFriendshipAreRespected() {
        UserStats hidden = mock(UserStats.class);
        hidden.hideOnline = true;
        User hiding = this.user(1, hidden);
        User actor = this.user(2, mock(UserStats.class));
        User offline = this.user(3, mock(UserStats.class));
        UserStats ignoring = mock(UserStats.class);
        when(ignoring.userIgnored(2)).thenReturn(true);
        User ignorer = this.user(4, ignoring);
        User oneSided = this.user(5, mock(UserStats.class));
        User friend = this.user(6, mock(UserStats.class));

        befriend(hiding, friend);
        befriend(friend, hiding);
        befriend(actor, offline);
        befriend(offline, actor);
        befriend(actor, ignorer);
        befriend(ignorer, actor);
        befriend(actor, oneSided);
        befriend(actor, friend);
        befriend(friend, actor);
        this.online.remove(3);
        this.wire();

        assertEquals(0, FriendActivityNotifier.roomEventStarted(hiding, "Party"), "hidden online status");
        assertEquals(1, FriendActivityNotifier.roomEventStarted(actor, "Party"));
        verify(friend.getClient()).sendResponse(any(ServerMessage.class));
        verify(ignorer.getClient(), never()).sendResponse(any(ServerMessage.class));
        verify(oneSided.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    @Test
    void eachKindHasItsSwitch() {
        User actor = this.user(7, mock(UserStats.class));
        User friend = this.user(8, mock(UserStats.class));
        befriend(actor, friend);
        befriend(friend, actor);
        this.settings.put(FriendActivityNotifier.QUESTS, false);
        this.wire();

        assertEquals(0, FriendActivityNotifier.questCompleted(actor, "2013CNY1", "1358934006765"));
        assertEquals(1, FriendActivityNotifier.playingGame(actor, FriendActivityNotifier.SNOWSTORM));

        this.settings.put(FriendActivityNotifier.ENABLED, false);
        assertEquals(0, FriendActivityNotifier.finishedGame(actor, FriendActivityNotifier.SNOWSTORM));
        assertEquals(0, FriendActivityNotifier.roomEventStarted(actor, ""), "nothing to show");
    }

    @Test
    void theServerSendsItAtTheMatchingMoments() throws Exception {
        String achievements = source("hotel/achievements/AchievementManager.java");
        String quests = source("hotel/quests/QuestManager.java");
        String promotion = source("messages/incoming/rooms/promotions/BuyRoomPromotionEvent.java");
        String snowwar = source("hotel/games/snowwar/SnowWarGame.java");

        assertTrue(achievements.contains("FriendActivityNotifier.achievementUnlocked(user, badge.getCode())"));
        assertTrue(quests.contains(
                "FriendActivityNotifier.questCompleted(user, quest.getCampaignCode(), quest.getCode())"));
        assertTrue(
                promotion.contains("if (newEvent)") && promotion.contains("FriendActivityNotifier.roomEventStarted("));
        assertTrue(snowwar.contains("FriendActivityNotifier.playingGame("));
        assertTrue(snowwar.contains("FriendActivityNotifier.finishedGame("));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/" + path));
    }

    private static String readString(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
