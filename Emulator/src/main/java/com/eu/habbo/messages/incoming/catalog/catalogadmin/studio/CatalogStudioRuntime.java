package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogAdminDraftMutationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogAdminSmartSaveService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftChangeSetService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftLifecycleService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftPreviewService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftValidationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveReconciliationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogOperationalOfferRepository;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPreviewPresentation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationHooks;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSnapshotThreeWayMerge;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioDocumentService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogUndoService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogChangeJournal;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogLiveProjection;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogLiveSnapshotRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogLockRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogOperationRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogSnapshotWriter;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogStudioQueryRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogValidationDataRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogVersionRepository;
import com.eu.habbo.habbohotel.catalog.versioning.RuntimeCatalogPreviewPresentationResolver;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.google.gson.Gson;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

public final class CatalogStudioRuntime {
    private static final AtomicReference<Services> SERVICES = new AtomicReference<>();

    private CatalogStudioRuntime() {}

    public static Services services() {
        Services current = SERVICES.get();
        if (current == null) {
            throw new IllegalStateException("Catalog Studio runtime has not been installed");
        }
        return current;
    }

    public static void install(DataSource dataSource, CatalogPublicationHooks publicationHooks) {
        install(dataSource, null, publicationHooks);
    }

    public static void install(
            DataSource dataSource, ItemManager itemManager, CatalogPublicationHooks publicationHooks) {
        Services created = create(
                Objects.requireNonNull(dataSource, "dataSource"),
                itemManager,
                Objects.requireNonNull(publicationHooks, "publicationHooks"));
        if (!SERVICES.compareAndSet(null, created)) {
            throw new IllegalStateException("Catalog Studio runtime has already been installed");
        }
    }

    private static Services create(
            DataSource dataSource, ItemManager itemManager, CatalogPublicationHooks publicationHooks) {
        CatalogVersionRepository versions = new JdbcCatalogVersionRepository();
        JdbcCatalogLockRepository lockRepository = new JdbcCatalogLockRepository(dataSource);
        JdbcCatalogChangeJournal journal = new JdbcCatalogChangeJournal();
        JdbcCatalogValidationDataRepository validationData = new JdbcCatalogValidationDataRepository();
        JdbcCatalogStudioQueryRepository queries = new JdbcCatalogStudioQueryRepository(dataSource);
        Gson gson = new Gson();
        JdbcCatalogSnapshotWriter snapshotWriter = new JdbcCatalogSnapshotWriter(gson);
        JdbcCatalogOperationRepository operationRepository = new JdbcCatalogOperationRepository();
        CatalogStudioDocumentService documents = new CatalogStudioDocumentService();
        CatalogOperationalOfferRepository operationalOffers = new CatalogOperationalOfferRepository(dataSource);
        CatalogLiveReconciliationService liveReconciliation = new CatalogLiveReconciliationService(
                new JdbcCatalogLiveSnapshotRepository(),
                new CatalogSnapshotThreeWayMerge(gson),
                versions,
                snapshotWriter,
                journal);
        return new Services(
                queries,
                new CatalogLockService(lockRepository),
                new CatalogAdminDraftMutationService(
                        dataSource, versions, journal, snapshotWriter, lockRepository, gson),
                new CatalogAdminSmartSaveService(
                        dataSource, versions, journal, snapshotWriter, lockRepository, operationRepository, gson),
                operationalOffers,
                new CatalogUndoService(dataSource, versions, journal, snapshotWriter, gson),
                new CatalogDraftValidationService(dataSource, versions, validationData),
                new CatalogPublicationService(
                        dataSource,
                        versions,
                        validationData,
                        liveReconciliation,
                        new JdbcCatalogLiveProjection(),
                        lockRepository,
                        publicationHooks),
                new CatalogDraftLifecycleService(dataSource, versions, lockRepository),
                new CatalogDraftPreviewService(
                        itemManager == null
                                ? ignored -> CatalogPreviewPresentation.empty()
                                : new RuntimeCatalogPreviewPresentationResolver(itemManager, operationalOffers)),
                documents,
                new CatalogDraftChangeSetService(
                        dataSource, versions, snapshotWriter, journal, lockRepository, documents));
    }

    public record Services(
            JdbcCatalogStudioQueryRepository queries,
            CatalogLockService locks,
            CatalogAdminDraftMutationService mutations,
            CatalogAdminSmartSaveService smartSaves,
            CatalogOperationalOfferRepository operationalOffers,
            CatalogUndoService undo,
            CatalogDraftValidationService validation,
            CatalogPublicationService publication,
            CatalogDraftLifecycleService lifecycle,
            CatalogDraftPreviewService preview,
            CatalogStudioDocumentService documents,
            CatalogDraftChangeSetService changeSets) {}
}
