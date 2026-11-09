package polaris.hotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import polaris.hotel.catalog.CatalogPageType;

class CatalogLiveUndoServiceTest {
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private static CatalogPageSnapshot page(String caption) {
        return new CatalogPageSnapshot(
                CatalogPageType.NORMAL,
                17,
                -1,
                "rares",
                caption,
                "default_3x3",
                1,
                1,
                1,
                1,
                true,
                true,
                false,
                "NORMAL",
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                "");
    }

    @Test
    void reversesTheWholeOperationInLiveAndKeepsAnAuditEntry() throws Exception {
        assertEquals(5, undo(page("Rares")));
    }

    @Test
    void refusesWhenThePageWasEditedOutsideCatalogStudioSince() {
        org.junit.jupiter.api.Assertions.assertThrows(
                CatalogUndoConflictException.class, () -> undo(page("Edited elsewhere")));
    }

    /** Undoes an edit that renamed page 17 to "Rares", while the live page is `livePage`. */
    private static long undo(CatalogPageSnapshot livePage) throws Exception {
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        CatalogVersionRepository versions = mock(CatalogVersionRepository.class);
        CatalogChangeJournal journal = mock(CatalogChangeJournal.class);
        CatalogOperationRepository operations = mock(CatalogOperationRepository.class);
        CatalogLiveEntityWriter live = mock(CatalogLiveEntityWriter.class);
        CatalogLiveMutationHook hook = mock(CatalogLiveMutationHook.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(versions.lockRuntimeState(connection)).thenReturn(new CatalogRuntimeState(10, 11, Instant.EPOCH));
        when(versions.loadSnapshot(connection, 10))
                .thenReturn(new CatalogVersionSnapshot(
                        new CatalogVersion(
                                10,
                                CatalogVersionStatus.PUBLISHED,
                                null,
                                4,
                                "Live",
                                1,
                                Instant.EPOCH,
                                1,
                                Instant.EPOCH),
                        List.of(livePage),
                        List.of()));
        CatalogChangeEntry original = new CatalogChangeEntry(
                1,
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                17,
                CatalogChangeOperation.UPDATE,
                GSON.toJson(page("Old name")),
                GSON.toJson(page("Rares")));
        when(journal.load(connection, 21))
                .thenReturn(new CatalogChangeGroup(
                        21, 10, 4, 7, "Edit", CatalogChangeSource.UI, Instant.EPOCH, List.of(original)));
        when(journal.hasLaterChangesToSameEntities(eq(connection), any())).thenReturn(false);
        when(versions.incrementRevision(connection, 10, 4)).thenReturn(5L);
        when(journal.append(eq(connection), eq(10L), eq(5L), eq(7), any(), eq(CatalogChangeSource.UNDO), any()))
                .thenReturn(22L);
        when(operations.findForUpdate(connection, "undo-21", 7)).thenReturn(Optional.empty());

        long revision = new CatalogLiveUndoService(
                        dataSource, versions, journal, (CatalogLiveSnapshotRepository) null, live, hook, operations)
                .undo(21, 7, 4, "undo-21");

        assertEquals(5, revision);
        verify(live)
                .apply(
                        eq(connection),
                        org.mockito.ArgumentMatchers.argThat(
                                change -> change.beforeJson().equals(GSON.toJson(page("Rares")))
                                        && change.afterJson().equals(GSON.toJson(page("Old name")))));
        verify(journal).append(eq(connection), eq(10L), eq(5L), eq(7), any(), eq(CatalogChangeSource.UNDO), any());
        verify(operations).insert(eq(connection), any(CatalogOperationRecord.class));
        verify(connection).commit();
        verify(hook).afterCommit(any(CatalogChangeEntry.class));
        return revision;
    }
}
