package polaris.hotel.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.quests.RewardTrackClaimResultComposer;
import polaris.messages.outgoing.quests.RewardTrackPremiumPurchaseResultComposer;
import polaris.messages.outgoing.quests.RewardTrackTextsComposer;
import polaris.messages.outgoing.quests.RewardTracksComposer;

/** What the official client needs to show the reward track: the login push and its result codes. */
class RewardTrackVisibilityTest {

    private static User user(int credits, int diamonds) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(7);
        when(info.getCredits()).thenReturn(credits);
        when(info.getCurrencyAmount(anyInt())).thenReturn(diamonds);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        return user;
    }

    private static List<MessageComposer> sent(User user) {
        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(user.getClient(), org.mockito.Mockito.atLeast(0)).sendResponse(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private static RewardTrackManager manager() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack introduction = new RewardTrack("introduction", "blue", 0, 0, 0, true, 1.5, 25, 5, 25);
        RewardTrack.Task chat = new RewardTrack.Task("chat_with_users", "chat_with_someone", "", false, 1);
        chat.addLevel(new RewardTrack.Level(5, 10, false));
        introduction.addTask(chat);
        introduction.addPrize(new RewardTrack.Prize("track_champ", 50, 4, "badge", "ACH_X1", 1, false, 1));
        manager.register(introduction);
        manager.register(new RewardTrack("free", "blue", 1, 0, 0, false, 0, 0, 0, 0));
        // a track whose window closed is not pushed
        manager.register(new RewardTrack("ended", "blue", 2, 0, 1, false, 0, 0, 0, 0));
        return manager;
    }

    @Test
    void loginPushesTheTextsThenTheActiveTracks() {
        RewardTrackManager manager = manager();
        User user = user(0, 0);

        manager.sendRewardTracksOnLogin(user);

        List<MessageComposer> packets = sent(user);
        assertEquals(2, packets.size());
        assertInstanceOf(RewardTrackTextsComposer.class, packets.get(0), "texts first, the window reads them");
        RewardTracksComposer tracks = assertInstanceOf(RewardTracksComposer.class, packets.get(1));
        assertFalse(tracks.isDisabled());
        assertFalse(tracks.isReload(), "a login push never pops the reload alert");
        assertEquals(
                List.of("introduction", "free"),
                tracks.getTracks().stream().map(RewardTracksComposer.Track::id).toList());
    }

    @Test
    void anEmptyHotelPushesTheDisabledFlag() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        User user = user(0, 0);

        manager.sendRewardTracksOnLogin(user);

        RewardTracksComposer tracks = (RewardTracksComposer) sent(user).get(1);
        assertTrue(tracks.isDisabled());
        assertTrue(tracks.getTracks().isEmpty());
    }

    @Test
    void aFailingPushNeverBreaksTheLogin() {
        RewardTrackManager manager = manager();
        User user = user(0, 0);
        GameClient client = user.getClient();
        doThrow(new IllegalStateException("socket")).when(client).sendResponse(any(MessageComposer.class));

        manager.sendRewardTracksOnLogin(user);
        manager.sendRewardTracksOnLogin(null);
    }

    @Test
    void theLoginHandlerPushesTheTracks() throws Exception {
        String login =
                Files.readString(Path.of("src/main/java/polaris/messages/incoming/handshake/SecureLoginEvent.java"));

        assertTrue(login.contains("QuestProgressEvents.loggedIn(user)"));
    }

    @Test
    void claimResultsUseTheOfficialCodes() {
        RewardTrackManager manager = manager();
        User user = user(0, 0);

        assertEquals(2, manager.claim(user, "nope", "track_champ"), "track not found");
        assertEquals(3, manager.claim(user, "introduction", "nope"), "reward not found");
        assertEquals(5, manager.claim(user, "introduction", "track_champ"), "not enough points");

        RewardTrackClaimResultComposer last = (RewardTrackClaimResultComposer) sent(user).stream()
                .filter(RewardTrackClaimResultComposer.class::isInstance)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals(5, last.getResultCode());
    }

    @Test
    void premiumResultsUseTheOfficialCodes() {
        RewardTrackManager manager = manager();

        assertEquals(2, manager.purchasePremium(user(100, 100), "nope"), "track not found");
        assertEquals(4, manager.purchasePremium(user(100, 100), "free"), "no premium on this track");
        assertEquals(7, manager.purchasePremium(user(0, 100), "introduction"), "not enough credits");
        assertEquals(8, manager.purchasePremium(user(100, 0), "introduction"), "not enough diamonds");

        User user = user(0, 0);
        manager.purchasePremium(user, "introduction");
        RewardTrackPremiumPurchaseResultComposer reply = (RewardTrackPremiumPurchaseResultComposer) sent(user).stream()
                .filter(RewardTrackPremiumPurchaseResultComposer.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertEquals(RewardTrackManager.PREMIUM_NOT_ENOUGH_CREDITS, reply.getResultCode());
    }
}
