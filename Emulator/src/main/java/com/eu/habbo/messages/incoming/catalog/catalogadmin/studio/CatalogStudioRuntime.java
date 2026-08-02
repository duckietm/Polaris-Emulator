package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogAdminDraftMutationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftChangeSetService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftLifecycleService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftPreviewService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftValidationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogOperationalOfferRepository;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioDocumentService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogUndoService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogChangeJournal;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogLiveProjection;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogLockRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogSnapshotWriter;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogStudioQueryRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogValidationDataRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogVersionRepository;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import com.google.gson.Gson;
import javax.sql.DataSource;

public final class CatalogStudioRuntime {
    private static volatile Services services;

    private CatalogStudioRuntime() {}

    public static Services services() {
        Services current = services;
        if (current != null) return current;
        synchronized (CatalogStudioRuntime.class) {
            current = services;
            if (current == null) {
                current = create(Emulator.getDatabase().getDataSource());
                services = current;
            }
            return current;
        }
    }

    private static Services create(DataSource dataSource) {
        CatalogVersionRepository versions = new JdbcCatalogVersionRepository();
        JdbcCatalogLockRepository lockRepository = new JdbcCatalogLockRepository(dataSource);
        JdbcCatalogChangeJournal journal = new JdbcCatalogChangeJournal();
        JdbcCatalogValidationDataRepository validationData = new JdbcCatalogValidationDataRepository();
        JdbcCatalogStudioQueryRepository queries = new JdbcCatalogStudioQueryRepository(dataSource);
        Gson gson = new Gson();
        JdbcCatalogSnapshotWriter snapshotWriter = new JdbcCatalogSnapshotWriter(gson);
        CatalogStudioDocumentService documents = new CatalogStudioDocumentService(gson);
        return new Services(
                queries,
                new CatalogLockService(lockRepository),
                new CatalogAdminDraftMutationService(
                        dataSource, versions, journal, snapshotWriter, lockRepository, gson),
                new CatalogOperationalOfferRepository(dataSource),
                new CatalogUndoService(dataSource, versions, journal, snapshotWriter, gson),
                new CatalogDraftValidationService(dataSource, versions, validationData),
                new CatalogPublicationService(
                        dataSource,
                        versions,
                        validationData,
                        new JdbcCatalogLiveProjection(),
                        lockRepository,
                        (published, nextDraftId) -> {
                            Emulator.getGameEnvironment().getCatalogManager().initialize();
                            Emulator.getGameServer()
                                    .getGameClientManager()
                                    .sendBroadcastResponse(new CatalogUpdatedComposer());
                        }),
                new CatalogDraftLifecycleService(dataSource, versions, lockRepository),
                new CatalogDraftPreviewService(),
                documents,
                new CatalogDraftChangeSetService(
                        dataSource, versions, snapshotWriter, journal, lockRepository, documents));
    }

    public record Services(
            JdbcCatalogStudioQueryRepository queries,
            CatalogLockService locks,
            CatalogAdminDraftMutationService mutations,
            CatalogOperationalOfferRepository operationalOffers,
            CatalogUndoService undo,
            CatalogDraftValidationService validation,
            CatalogPublicationService publication,
            CatalogDraftLifecycleService lifecycle,
            CatalogDraftPreviewService preview,
            CatalogStudioDocumentService documents,
            CatalogDraftChangeSetService changeSets) {}
}
