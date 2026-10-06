package com.eu.habbo.habbohotel.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackPremiumPurchaseResultComposer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Reward track payment, claim and load failures must never pay twice, take money for nothing or wipe progress. */
class RewardTrackSafetyTest {

    private static Habbo habbo(int userId) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(userId);
        when(info.getCredits()).thenReturn(1000);
        when(info.getCurrencyAmount(anyInt())).thenReturn(1000);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(mock(HabboStats.class));
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        return habbo;
    }

    private static RewardTrack track() {
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, true, 1.5, 50, 25, 10);
        track.addPrize(new RewardTrack.Prize("p1", 0, 0, "credits", "", 5, false, 1));
        return track;
    }

    /** A manager without a database whose storage hooks the tests can steer. */
    private static class Fixture extends RewardTrackManager {
        final List<List<EconomyOperation>> payments = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        Exception paymentFailure;
        Boolean claimInserted = true;
        boolean failLoads;
        final AtomicInteger loads = new AtomicInteger();

        Fixture() {
            super(false);
            this.register(track());
        }

        @Override
        protected UserRewardTrackState load(int userId, String trackId) {
            this.loads.incrementAndGet();
            return this.failLoads ? null : new UserRewardTrackState(trackId, 100, false);
        }

        @Override
        protected void applyPayment(Habbo habbo, List<EconomyOperation> operations) throws SQLException {
            if (this.paymentFailure instanceof SQLException sql) throw sql;
            if (this.paymentFailure instanceof RuntimeException runtime) throw runtime;
            this.payments.add(operations);
            this.events.add("paid");
        }

        @Override
        protected boolean saveTrackNow(int userId, UserRewardTrackState state) {
            this.events.add("saved premium=" + state.isPremium());
            return true;
        }

        @Override
        protected boolean recordClaim(int userId, String trackId, String prizeId) {
            if (this.claimInserted == null) {
                throw new SqlQueries.DataAccessException("down", new SQLException("down"));
            }
            return this.claimInserted;
        }
    }

    @Test
    void premiumIsNotGrantedWhenTheLedgerRefusesThePayment() {
        Fixture manager = new Fixture();
        Habbo habbo = habbo(7);
        manager.paymentFailure = new SQLException("ledger down");

        assertEquals(RewardTrackManager.RESULT_UNKNOWN, manager.purchasePremium(habbo, "season_1"));
        UserRewardTrackState state = manager.stateFor(habbo, manager.getTrack("season_1"));
        assertFalse(state.isPremium());
        assertEquals(100, state.getPoints(), "no instant points without payment");

        manager.paymentFailure = new IllegalArgumentException("balance");
        assertEquals(RewardTrackManager.RESULT_NOT_ENOUGH_CURRENCY, manager.purchasePremium(habbo, "season_1"));
        assertFalse(state.isPremium());
        assertTrue(manager.events.isEmpty(), "nothing is saved for a failed payment");
    }

    @Test
    void premiumIsPaidInOneBatchWithFixedIdsAndSavedBeforeTheReply() {
        Fixture manager = new Fixture();
        Habbo habbo = habbo(7);
        GameClient client = habbo.getClient();
        doAnswer(invocation -> {
                    MessageComposer composer = invocation.getArgument(0);
                    if (composer instanceof RewardTrackPremiumPurchaseResultComposer) manager.events.add("reply");
                    return null;
                })
                .when(client)
                .sendResponse(any(MessageComposer.class));

        assertEquals(RewardTrackManager.RESULT_OK, manager.purchasePremium(habbo, "season_1"));

        assertEquals(1, manager.payments.size(), "credits and diamonds are one ledger transaction");
        List<EconomyOperation> batch = manager.payments.getFirst();
        assertEquals(2, batch.size());
        assertEquals("reward_track_premium:7:season_1:credits", batch.get(0).operationId());
        assertEquals(EconomyLedger.CREDITS, batch.get(0).currencyType());
        assertEquals(-10, batch.get(0).delta());
        assertEquals("reward_track_premium:7:season_1:diamonds", batch.get(1).operationId());
        assertEquals(QuestRewards.DIAMONDS_POINT_TYPE, batch.get(1).currencyType());
        assertEquals(-25, batch.get(1).delta());
        assertEquals(List.of("paid", "saved premium=true", "reply"), manager.events);
        assertEquals(150, manager.stateFor(habbo, manager.getTrack("season_1")).getPoints());
    }

    @Test
    void aFreePassTakesNoPayment() {
        RewardTrack free = new RewardTrack("free", "blue", 1, 0, 0, true, 1, 0, 0, 0);
        assertTrue(RewardTrackManager.premiumOperations(7, free).isEmpty());
    }

    @Test
    void aClaimRowThatAlreadyExistsGrantsNothing() {
        Fixture manager = new Fixture();
        Habbo habbo = habbo(7);
        manager.claimInserted = false;

        assertEquals(RewardTrackManager.RESULT_ALREADY_CLAIMED, manager.claim(habbo, "season_1", "p1"));
        verify(habbo, never()).giveCredits(anyInt(), anyString(), anyString(), any());
        verify(habbo, never()).giveCredits(anyInt(), anyString());
        assertTrue(manager.stateFor(habbo, manager.getTrack("season_1")).isClaimed("p1"));
    }

    @Test
    void aFailedClaimWriteGrantsNothingAndCanBeRetried() {
        Fixture manager = new Fixture();
        Habbo habbo = habbo(7);
        manager.claimInserted = null;

        assertEquals(RewardTrackManager.RESULT_UNKNOWN, manager.claim(habbo, "season_1", "p1"));
        verify(habbo, never()).giveCredits(anyInt(), anyString(), anyString(), any());
        assertFalse(manager.stateFor(habbo, manager.getTrack("season_1")).isClaimed("p1"));

        manager.claimInserted = true;
        assertEquals(RewardTrackManager.RESULT_OK, manager.claim(habbo, "season_1", "p1"));
        verify(habbo).giveCredits(5, "quests.reward", "reward_track:7:season_1:p1", 7);
        assertEquals(RewardTrackManager.RESULT_ALREADY_CLAIMED, manager.claim(habbo, "season_1", "p1"));
    }

    @Test
    void aFailedLoadIsNotCachedAndFailsTheAction() {
        Fixture manager = new Fixture();
        Habbo habbo = habbo(7);
        RewardTrack track = manager.getTrack("season_1");
        manager.failLoads = true;

        assertNull(manager.stateFor(habbo, track));
        assertEquals(RewardTrackManager.RESULT_UNKNOWN, manager.claim(habbo, "season_1", "p1"));
        assertEquals(RewardTrackManager.RESULT_UNKNOWN, manager.purchasePremium(habbo, "season_1"));
        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);
        assertNotNull(manager.rewardTracks(habbo, false));
        assertTrue(manager.payments.isEmpty());
        assertTrue(manager.events.isEmpty(), "an unloaded state is never saved");

        manager.failLoads = false;
        UserRewardTrackState state = manager.stateFor(habbo, track);
        assertNotNull(state, "the next access loads again");
        assertEquals(100, state.getPoints());
        int loads = manager.loads.get();
        manager.stateFor(habbo, track);
        assertEquals(loads, manager.loads.get(), "a successful load is cached");
    }

    @Test
    void operationIdsAreDeterministicAndFitTheLedgerColumn() {
        String longId = "x".repeat(64);
        String prize = RewardTrackManager.prizeOperationId(2_000_000_000, longId, longId);
        String premium = RewardTrackManager.premiumOperationId(2_000_000_000, longId, "diamonds");

        assertTrue(prize.length() <= 96, prize);
        assertTrue(premium.length() <= 96, premium);
        assertEquals(prize, RewardTrackManager.prizeOperationId(2_000_000_000, longId, longId));
        assertNotEquals(prize, RewardTrackManager.prizeOperationId(2_000_000_000, longId, longId + "y"));
        assertTrue(premium.startsWith("reward_track_premium:2000000000:") && premium.endsWith(":diamonds"));
        assertNotEquals(premium, RewardTrackManager.premiumOperationId(2_000_000_000, longId, "credits"));
        assertEquals("reward_track:3:s:p", RewardTrackManager.prizeOperationId(3, "s", "p"));
    }
}
