package com.eu.habbo.habbohotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnowWarGameChargesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    /** In-memory allowance with the same rules as the SQL one. */
    private static final class FakeAllowance implements SnowWarGameAllowance {
        final Map<Integer, Integer> tokens = new HashMap<>();
        final Map<Integer, Integer> freeUsed = new HashMap<>();
        int consumeCalls = 0;
        boolean failing = false;

        @Override
        public ConsumeResult tryConsumeGame(int userId, LocalDate today, int freePerDay) {
            this.consumeCalls++;
            if (freePerDay < 0) {
                return ConsumeResult.UNLIMITED;
            }
            if (this.failing) {
                return ConsumeResult.ERROR;
            }
            int used = this.freeUsed.getOrDefault(userId, 0);
            if (used < freePerDay) {
                this.freeUsed.put(userId, used + 1);
                return ConsumeResult.FREE;
            }
            int bought = this.tokens.getOrDefault(userId, 0);
            if (bought > 0) {
                this.tokens.put(userId, bought - 1);
                return ConsumeResult.TOKEN;
            }
            return ConsumeResult.NO_GAMES;
        }

        @Override
        public boolean refundGame(int userId) {
            this.tokens.merge(userId, 1, Integer::sum);
            return true;
        }

        @Override
        public int gamesLeft(int userId, LocalDate today, int freePerDay) {
            if (freePerDay < 0) {
                return -1;
            }
            return Math.max(0, freePerDay - this.freeUsed.getOrDefault(userId, 0))
                    + this.tokens.getOrDefault(userId, 0);
        }
    }

    @Test
    void usesFreeGamesFirstThenBoughtGamesThenRefuses() {
        FakeAllowance allowance = new FakeAllowance();
        allowance.tokens.put(1, 1);
        SnowWarGameCharges charges = new SnowWarGameCharges(allowance, () -> DAY, () -> 1);

        assertEquals(2, charges.gamesLeft(1));
        assertEquals(SnowWarGameAllowance.ConsumeResult.FREE, charges.charge(1));
        charges.started(List.of(1));
        assertEquals(SnowWarGameAllowance.ConsumeResult.TOKEN, charges.charge(1));
        charges.started(List.of(1));
        assertEquals(SnowWarGameAllowance.ConsumeResult.NO_GAMES, charges.charge(1));
        assertEquals(0, charges.gamesLeft(1));
    }

    @Test
    void neverChargesAPlayerTwiceForTheSameFormingMatch() {
        FakeAllowance allowance = new FakeAllowance();
        SnowWarGameCharges charges = new SnowWarGameCharges(allowance, () -> DAY, () -> 5);

        assertTrue(charges.charge(1).charged());
        // The match fell short and the player was requeued: the next attempt is free.
        assertTrue(charges.charge(1).allowed());
        assertEquals(1, allowance.consumeCalls);
        assertEquals(4, charges.gamesLeft(1));
    }

    @Test
    void refundsAPaidPlayerWhoLeavesBeforeTheMatchStarts() {
        FakeAllowance allowance = new FakeAllowance();
        SnowWarGameCharges charges = new SnowWarGameCharges(allowance, () -> DAY, () -> 1);

        charges.charge(1);
        assertTrue(charges.refundIfPaid(1));
        assertFalse(charges.refundIfPaid(1));
        assertEquals(1, allowance.tokens.get(1));
    }

    @Test
    void aStartedMatchIsNotRefundedOnLeave() {
        FakeAllowance allowance = new FakeAllowance();
        SnowWarGameCharges charges = new SnowWarGameCharges(allowance, () -> DAY, () -> 1);

        charges.charge(1);
        charges.started(List.of(1));
        assertFalse(charges.refundIfPaid(1));
        assertFalse(allowance.tokens.containsKey(1));
    }

    @Test
    void unlimitedAndFailedChargesAreNotMarkedPaid() {
        FakeAllowance allowance = new FakeAllowance();
        SnowWarGameCharges unlimited = new SnowWarGameCharges(allowance, () -> DAY, () -> -1);
        assertEquals(SnowWarGameAllowance.ConsumeResult.UNLIMITED, unlimited.charge(1));
        assertFalse(unlimited.hasPaid(1));
        assertEquals(-1, unlimited.gamesLeft(1));

        allowance.failing = true;
        SnowWarGameCharges limited = new SnowWarGameCharges(allowance, () -> DAY, () -> 1);
        SnowWarGameAllowance.ConsumeResult result = limited.charge(2);
        assertEquals(SnowWarGameAllowance.ConsumeResult.ERROR, result);
        assertFalse(result.allowed());
        assertFalse(limited.hasPaid(2));
    }
}
