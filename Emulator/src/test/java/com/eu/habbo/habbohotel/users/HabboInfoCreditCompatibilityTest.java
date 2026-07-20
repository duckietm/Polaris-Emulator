package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HabboInfoCreditCompatibilityTest {
    @Test
    void setCreditsIsAnAbsoluteSynchronousSave() {
        RecordingHabboInfo info = new RecordingHabboInfo(100);

        info.setCredits(250);

        assertEquals(250, info.getCredits());
        assertEquals(1, info.saveCount);
    }

    @Test
    void setCreditsRejectsNegativeBalancesBeforeSaving() {
        RecordingHabboInfo info = new RecordingHabboInfo(100);

        assertThrows(IllegalArgumentException.class, () -> info.setCredits(-1));

        assertEquals(100, info.getCredits());
        assertEquals(0, info.saveCount);
    }

    @Test
    void addCreditsAppliesADeltaClampsAndSavesSynchronously() {
        RecordingHabboInfo info = new RecordingHabboInfo(100);

        info.addCredits(50);
        assertEquals(150, info.getCredits());
        assertEquals(1, info.saveCount);

        info.addCredits(-200);
        assertEquals(0, info.getCredits());
        assertEquals(2, info.saveCount);

        info.setCredits(Integer.MAX_VALUE);
        info.addCredits(1);
        assertEquals(Integer.MAX_VALUE, info.getCredits());
        assertEquals(4, info.saveCount);
    }

    @Test
    void committedLedgerBalanceUpdatesMemoryWithoutRunningLegacyPersistence() {
        RecordingHabboInfo info = new RecordingHabboInfo(100);

        info.applyPersistedCredits(275);

        assertEquals(275, info.getCredits());
        assertEquals(0, info.saveCount);
        assertThrows(IllegalArgumentException.class, () -> info.applyPersistedCredits(-1));
        assertEquals(275, info.getCredits());
        assertEquals(0, info.saveCount);
    }

    private static final class RecordingHabboInfo extends HabboInfo {
        private int saveCount;

        private RecordingHabboInfo(int credits) {
            super(42, credits);
        }

        @Override
        public void run() {
            this.saveCount++;
        }
    }
}
