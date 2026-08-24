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
    private final SoundboardSound staffSound = new SoundboardSound(8, "Staff", "staff", "/sounds/soundboard/staff.mp3", 5);
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
    void unknownOrInvalidRankCooldownFallsBackToSixtySeconds() {
        assertEquals(60, this.manager.getCooldownSecondsForRank(99));
    }

    @Test
    void keepsOrderedSoundsAndUsesTheFirstDuplicateIdForLookup() {
        SoundboardSound duplicate = new SoundboardSound(this.publicSound.id, "Duplicate", "duplicate", "/sounds/duplicate.mp3", 1);
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

        SoundboardCatalogResult result =
                catalogManager.upsert(42, new SoundboardCatalogCommand(7, "Changed", "changed", "/changed.mp3", 1, true));

        assertEquals(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE, result.code());
        assertSame(this.publicSound, catalogManager.getSound(this.publicSound.id));
        assertEquals(List.of(this.publicSound), catalogManager.getCatalog());
    }
}
