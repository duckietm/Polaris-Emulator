package com.eu.habbo.habbohotel.soundboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoundboardManagerContractTest {

    private final SoundboardSound publicSound =
            new SoundboardSound(7, "Campanella", "campanella", "/sounds/soundboard/campanella.mp3", 1);
    private final SoundboardSound staffSound =
            new SoundboardSound(8, "Staff", "staff", "/sounds/soundboard/staff.mp3", 5);
    private final SoundboardManager manager =
            new SoundboardManager(List.of(this.publicSound, this.staffSound), rankId -> rankId == 5 ? 10 : -1);

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

        SoundboardManager.PlayDecision retry = this.manager.tryPlay(10, 5, this.staffSound.id, 2_000L);

        assertFalse(retry.allowed());
        assertEquals(SoundboardManager.DenialReason.COOLDOWN, retry.denialReason());
        assertEquals(9, retry.remainingSeconds());
    }

    @Test
    void aPadCooldownHoldsOnlyThatPadForThatPlayer() {
        SoundboardSound slow = new SoundboardSound(11, "Slow", "slow", "", true, 30, 1, 20);
        SoundboardSound other = new SoundboardSound(12, "Other", "other", "", true, 40, 1, 0);
        SoundboardManager padManager = new SoundboardManager(List.of(slow, other), rankId -> 1);

        assertTrue(padManager.tryPlay(10, 1, slow.id, 1_000L).allowed());

        SoundboardManager.PlayDecision again = padManager.tryPlay(10, 1, slow.id, 3_000L);
        assertFalse(again.allowed());
        assertEquals(SoundboardManager.DenialReason.PAD_COOLDOWN, again.denialReason());
        assertEquals(18, again.remainingSeconds());

        assertTrue(padManager.tryPlay(10, 1, other.id, 3_000L).allowed());
        assertTrue(padManager.tryPlay(11, 1, slow.id, 3_000L).allowed());
        assertTrue(padManager.tryPlay(10, 1, slow.id, 21_000L).allowed());
    }

    @Test
    void aPadTurnedAwayByItsCooldownDoesNotSpendTheRankCooldown() {
        SoundboardSound slow = new SoundboardSound(11, "Slow", "slow", "", true, 30, 1, 20);
        SoundboardSound other = new SoundboardSound(12, "Other", "other", "", true, 40, 1, 0);
        SoundboardManager padManager = new SoundboardManager(List.of(slow, other), rankId -> 5);

        assertTrue(padManager.tryPlay(10, 1, slow.id, 1_000L).allowed());
        assertEquals(
                SoundboardManager.DenialReason.PAD_COOLDOWN,
                padManager.tryPlay(10, 1, slow.id, 5_000L).denialReason());

        // The rank cooldown started at 1 000 ms and ends at 6 000 ms; the refused attempt did not move it.
        assertTrue(padManager.tryPlay(10, 1, other.id, 6_000L).allowed());
    }

    @Test
    void unknownOrInvalidRankCooldownFallsBackToSixtySeconds() {
        assertEquals(60, this.manager.getCooldownSecondsForRank(99));
    }

    @Test
    void keepsOrderedSoundsAndUsesTheFirstDuplicateIdForLookup() {
        SoundboardSound duplicate =
                new SoundboardSound(this.publicSound.id, "Duplicate", "duplicate", "/sounds/duplicate.mp3", 1);
        SoundboardManager duplicateManager =
                new SoundboardManager(List.of(this.publicSound, duplicate, this.staffSound), rankId -> 0);

        assertEquals(List.of(this.publicSound, duplicate, this.staffSound), duplicateManager.getSounds());
        assertSame(this.publicSound, duplicateManager.getSound(this.publicSound.id));
        assertSame(this.staffSound, duplicateManager.getSound(this.staffSound.id));
        assertNull(duplicateManager.getSound(999));
    }

    @Test
    void disabledSoundsRemainInCatalogButCannotBePlayed() {
        SoundboardSound disabled = new SoundboardSound(9, "Disabled", "disabled", "/sounds/disabled.mp3", false, 5, 1);
        SoundboardManager catalogManager =
                new SoundboardManager(List.of(this.publicSound, disabled), rankId -> 0, rankId -> true, null);

        assertEquals(List.of(this.publicSound, disabled), catalogManager.getCatalog());
        assertEquals(List.of(this.publicSound), catalogManager.getSounds());
        assertNull(catalogManager.getSound(disabled.id));
        assertFalse(catalogManager.tryPlay(10, 7, disabled.id, 1_000L).allowed());
    }

    @Test
    void failedMutationKeepsThePublishedSnapshot() {
        SoundboardCatalogRepository repository = mock(SoundboardCatalogRepository.class);
        SoundboardManager catalogManager =
                new SoundboardManager(List.of(this.publicSound), rankId -> 0, rankId -> true, repository);
        when(repository.upsert(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE));

        SoundboardCatalogResult result = catalogManager.upsert(
                42, new SoundboardCatalogCommand(7, "Changed", "changed", "/changed.mp3", 1, true));

        assertEquals(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE, result.code());
        assertSame(this.publicSound, catalogManager.getSound(this.publicSound.id));
        assertEquals(List.of(this.publicSound), catalogManager.getCatalog());
    }
}
