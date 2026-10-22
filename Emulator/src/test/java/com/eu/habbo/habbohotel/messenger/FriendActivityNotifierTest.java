package com.eu.habbo.habbohotel.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ServerMessage;
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

/** AIR {@code HabboFriendBarData.makeNotification}: online friends see what a friend does on their friend bar. */
class FriendActivityNotifierTest {
    private final Map<Integer, Habbo> online = new HashMap<>();
    private final Map<String, Boolean> settings = new HashMap<>();

    private Habbo user(int id, HabboStats stats) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        Messenger messenger = mock(Messenger.class);
        when(messenger.getFriends()).thenReturn(new ConcurrentHashMap<>());
        when(habbo.getMessenger()).thenReturn(messenger);
        this.online.put(id, habbo);
        return habbo;
    }

    private static void befriend(Habbo one, Habbo other) {
        int otherId = other.getHabboInfo().getId();
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
        Habbo actor = this.user(7, mock(HabboStats.class));
        Habbo friend = this.user(8, mock(HabboStats.class));
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
        HabboStats hidden = mock(HabboStats.class);
        hidden.hideOnline = true;
        Habbo hiding = this.user(1, hidden);
        Habbo actor = this.user(2, mock(HabboStats.class));
        Habbo offline = this.user(3, mock(HabboStats.class));
        HabboStats ignoring = mock(HabboStats.class);
        when(ignoring.userIgnored(2)).thenReturn(true);
        Habbo ignorer = this.user(4, ignoring);
        Habbo oneSided = this.user(5, mock(HabboStats.class));
        Habbo friend = this.user(6, mock(HabboStats.class));

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
        Habbo actor = this.user(7, mock(HabboStats.class));
        Habbo friend = this.user(8, mock(HabboStats.class));
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
        String achievements = source("habbohotel/achievements/AchievementManager.java");
        String quests = source("habbohotel/quests/QuestManager.java");
        String promotion = source("messages/incoming/rooms/promotions/BuyRoomPromotionEvent.java");
        String snowwar = source("habbohotel/games/snowwar/SnowWarGame.java");

        assertTrue(achievements.contains("FriendActivityNotifier.achievementUnlocked(habbo, badge.getCode())"));
        assertTrue(quests.contains(
                "FriendActivityNotifier.questCompleted(habbo, quest.getCampaignCode(), quest.getCode())"));
        assertTrue(
                promotion.contains("if (newEvent)") && promotion.contains("FriendActivityNotifier.roomEventStarted("));
        assertTrue(snowwar.contains("FriendActivityNotifier.playingGame("));
        assertTrue(snowwar.contains("FriendActivityNotifier.finishedGame("));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/" + path));
    }

    private static String readString(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
