package com.eu.habbo.habbohotel.soundboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundboardManagerContractTest {

    private final SoundboardSound publicSound =
            new SoundboardSound(7, "Campanella", "/sounds/soundboard/campanella.mp3", 1);
    private final SoundboardSound staffSound =
            new SoundboardSound(8, "Staff", "/sounds/soundboard/staff.mp3", 5);
    private final SoundboardManager manager = new SoundboardManager(
            List.of(this.publicSound, this.staffSound),
            rankId -> rankId == 5 ? 10 : -1);

    @Test
    void returnsOnlySoundsAvailableToTheRecipientRank() {
        assertEquals(List.of(this.publicSound), this.manager.getSoundsForRank(1));
        assertEquals(List.of(this.publicSound, this.staffSound), this.manager.getSoundsForRank(5));
        assertThrows(
                UnsupportedOperationException.class,
                () -> this.manager.getSoundsForRank(1).add(this.staffSound));
    }

    @Test
    void rejectsRestrictedSoundsBeforeAcquiringCooldown() {
        assertFalse(this.manager.tryPlay(10, 1, this.staffSound.id, 1_000L).allowed());
        assertTrue(this.manager.tryPlay(10, 5, this.staffSound.id, 1_000L).allowed());
    }

    @Test
    void appliesTheRankCooldownGloballyPerAccount() {
        assertTrue(this.manager.tryPlay(10, 5, this.staffSound.id, 1_000L).allowed());

        SoundboardManager.PlayDecision retry =
                this.manager.tryPlay(10, 5, this.staffSound.id, 2_000L);

        assertFalse(retry.allowed());
        assertEquals(SoundboardManager.DenialReason.COOLDOWN, retry.denialReason());
        assertEquals(9, retry.remainingSeconds());
    }

    @Test
    void unknownOrInvalidRankCooldownFallsBackToSixtySeconds() {
        assertEquals(60, this.manager.getCooldownSecondsForRank(99));
    }
}
