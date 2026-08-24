package com.eu.habbo.habbohotel.soundboard;

import static com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult.Code.INVALID_NAME;
import static com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult.Code.INVALID_ORDER;
import static com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult.Code.INVALID_RANK;
import static com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult.Code.INVALID_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoundboardCatalogPolicyTest {

    @Test
    void acceptsRelativeAndHttpUrlsOnly() {
        assertTrue(SoundboardUrlPolicy.isAllowed("/sounds/soundboard/bell.mp3"));
        assertTrue(SoundboardUrlPolicy.isAllowed("https://cdn.example.test/bell.mp3"));
        assertTrue(SoundboardUrlPolicy.isAllowed("http://cdn.example.test/bell.mp3"));
        assertFalse(SoundboardUrlPolicy.isAllowed("javascript:alert(1)"));
        assertFalse(SoundboardUrlPolicy.isAllowed("data:audio/mp3;base64,AAAA"));
        assertFalse(SoundboardUrlPolicy.isAllowed("blob:https://example.test/id"));
        assertFalse(SoundboardUrlPolicy.isAllowed("file:///tmp/bell.mp3"));
    }

    @Test
    void rejectsInvalidDraftsBeforeRepositoryMutation() {
        SoundboardCatalogRepository repository = mock(SoundboardCatalogRepository.class);
        SoundboardManager manager =
                new SoundboardManager(List.of(), rankId -> 0, rankId -> rankId == 1 || rankId == 7, repository);

        assertEquals(
                INVALID_NAME,
                manager.upsert(42, new SoundboardCatalogCommand(0, " ", "bell", "/bell.mp3", 1, true))
                        .code());
        assertEquals(
                INVALID_NAME,
                manager.upsert(42, new SoundboardCatalogCommand(0, "x".repeat(65), "bell", "/bell.mp3", 1, true))
                        .code());
        assertEquals(
                INVALID_URL,
                manager.upsert(42, new SoundboardCatalogCommand(0, "Bell", "", "javascript:alert(1)", 1, true))
                        .code());
        assertEquals(
                INVALID_RANK,
                manager.upsert(42, new SoundboardCatalogCommand(0, "Bell", "bell", "/bell.mp3", 6, true))
                        .code());

        verify(repository, never()).upsert(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reorderMustContainEveryCatalogIdExactlyOnce() {
        SoundboardCatalogRepository repository = mock(SoundboardCatalogRepository.class);
        SoundboardSound first = new SoundboardSound(1, "First", "first", "/first.mp3", true, 10, 1);
        SoundboardSound second = new SoundboardSound(2, "Second", "second", "/second.mp3", true, 20, 1);
        SoundboardManager manager =
                new SoundboardManager(List.of(first, second), rankId -> 0, rankId -> true, repository);

        assertEquals(INVALID_ORDER, manager.reorder(42, List.of(1)).code());
        assertEquals(INVALID_ORDER, manager.reorder(42, List.of(1, 1)).code());
        assertEquals(INVALID_ORDER, manager.reorder(42, List.of(1, 99)).code());

        verify(repository, never())
                .reorder(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyList());
    }
}
