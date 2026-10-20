package com.eu.habbo.habbohotel.quests;

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

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackClaimResultComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackPremiumPurchaseResultComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackTextsComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTracksComposer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** What the official client needs to show the reward track: the login push and its result codes. */
class RewardTrackVisibilityTest {

    private static Habbo habbo(int credits, int diamonds) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(7);
        when(info.getCredits()).thenReturn(credits);
        when(info.getCurrencyAmount(anyInt())).thenReturn(diamonds);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        return habbo;
    }

    private static List<MessageComposer> sent(Habbo habbo) {
        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(habbo.getClient(), org.mockito.Mockito.atLeast(0)).sendResponse(captor.capture());
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
        Habbo habbo = habbo(0, 0);

        manager.sendRewardTracksOnLogin(habbo);

        List<MessageComposer> packets = sent(habbo);
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
        Habbo habbo = habbo(0, 0);

        manager.sendRewardTracksOnLogin(habbo);

        RewardTracksComposer tracks = (RewardTracksComposer) sent(habbo).get(1);
        assertTrue(tracks.isDisabled());
        assertTrue(tracks.getTracks().isEmpty());
    }

    @Test
    void aFailingPushNeverBreaksTheLogin() {
        RewardTrackManager manager = manager();
        Habbo habbo = habbo(0, 0);
        GameClient client = habbo.getClient();
        doThrow(new IllegalStateException("socket")).when(client).sendResponse(any(MessageComposer.class));

        manager.sendRewardTracksOnLogin(habbo);
        manager.sendRewardTracksOnLogin(null);
    }

    @Test
    void theLoginHandlerPushesTheTracks() throws Exception {
        String login = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java"));

        assertTrue(login.contains("QuestProgressEvents.loggedIn(habbo)"));
    }

    @Test
    void claimResultsUseTheOfficialCodes() {
        RewardTrackManager manager = manager();
        Habbo habbo = habbo(0, 0);

        assertEquals(2, manager.claim(habbo, "nope", "track_champ"), "track not found");
        assertEquals(3, manager.claim(habbo, "introduction", "nope"), "reward not found");
        assertEquals(5, manager.claim(habbo, "introduction", "track_champ"), "not enough points");

        RewardTrackClaimResultComposer last = (RewardTrackClaimResultComposer) sent(habbo).stream()
                .filter(RewardTrackClaimResultComposer.class::isInstance)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals(5, last.getResultCode());
    }

    @Test
    void premiumResultsUseTheOfficialCodes() {
        RewardTrackManager manager = manager();

        assertEquals(2, manager.purchasePremium(habbo(100, 100), "nope"), "track not found");
        assertEquals(4, manager.purchasePremium(habbo(100, 100), "free"), "no premium on this track");
        assertEquals(7, manager.purchasePremium(habbo(0, 100), "introduction"), "not enough credits");
        assertEquals(8, manager.purchasePremium(habbo(100, 0), "introduction"), "not enough diamonds");

        Habbo habbo = habbo(0, 0);
        manager.purchasePremium(habbo, "introduction");
        RewardTrackPremiumPurchaseResultComposer reply = (RewardTrackPremiumPurchaseResultComposer) sent(habbo).stream()
                .filter(RewardTrackPremiumPurchaseResultComposer.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertEquals(RewardTrackManager.PREMIUM_NOT_ENOUGH_CREDITS, reply.getResultCode());
    }
}
