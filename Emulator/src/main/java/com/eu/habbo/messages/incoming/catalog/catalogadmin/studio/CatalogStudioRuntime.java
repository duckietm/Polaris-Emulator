package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.CatalogAdminCacheSync;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveChangeSetService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveMutationHook;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveMutationService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveUndoService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveValidationGuard;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogOperationalOfferRepository;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationHooks;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioDocumentService;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogChangeJournal;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogLiveEntityWriter;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogLiveSnapshotRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogOperationRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogStudioQueryRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogValidationDataRepository;
import com.eu.habbo.habbohotel.catalog.versioning.JdbcCatalogVersionRepository;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.google.gson.Gson;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

public final class CatalogStudioRuntime {
    private static final AtomicReference<Services> SERVICES = new AtomicReference<>();

    private CatalogStudioRuntime() {}

    public static Services services() {
        Services current = SERVICES.get();
        if (current == null) throw new IllegalStateException("Catalog Manager runtime has not been installed");
        return current;
    }

    public static void install(DataSource dataSource, CatalogPublicationHooks unusedHooks) {
        install(dataSource, null, unusedHooks);
    }

    public static void install(DataSource dataSource, ItemManager itemManager, CatalogPublicationHooks unusedHooks) {
        Services created = create(Objects.requireNonNull(dataSource, "dataSource"));
        if (!SERVICES.compareAndSet(null, created)) {
            throw new IllegalStateException("Catalog Manager runtime has already been installed");
        }
    }

    private static Services create(DataSource dataSource) {
        CatalogVersionRepository state = new JdbcCatalogVersionRepository();
        JdbcCatalogChangeJournal history = new JdbcCatalogChangeJournal();
        JdbcCatalogValidationDataRepository validationData = new JdbcCatalogValidationDataRepository();
        JdbcCatalogStudioQueryRepository queries = new JdbcCatalogStudioQueryRepository(dataSource);
        Gson gson = new Gson();
        JdbcCatalogOperationRepository operations = new JdbcCatalogOperationRepository();
        CatalogStudioDocumentService documents = new CatalogStudioDocumentService();
        // Reading the catalog is proportional to its size. Operator reads run here so a large catalog cannot stall
        // the thread that serves the operator's other packets.
        ExecutorService reads = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CatalogManagerReads");
            thread.setDaemon(true);
            return thread;
        });
        CatalogOperationalOfferRepository operationalOffers = new CatalogOperationalOfferRepository(dataSource);
        JdbcCatalogLiveEntityWriter liveWriter = new JdbcCatalogLiveEntityWriter(gson);
        JdbcCatalogLiveSnapshotRepository liveSnapshots = new JdbcCatalogLiveSnapshotRepository();
        CatalogLiveValidationGuard liveValidation = new CatalogLiveValidationGuard(validationData, gson);
        CatalogLiveMutationHook liveHook = change -> {
            if (change.entityType() == com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType.PAGE) {
                CatalogAdminCacheSync.refreshPageFlagsFromDb(change.entityId(), change.catalogType());
            } else {
                CatalogAdminCacheSync.reloadCatalogItem(change.entityId(), change.catalogType());
            }
        };
        return new Services(
                reads,
                queries,
                new CatalogLiveMutationService(
                        dataSource,
                        state,
                        history,
                        liveSnapshots,
                        liveWriter,
                        liveHook,
                        liveValidation,
                        operations,
                        gson),
                new CatalogLiveChangeSetService(
                        dataSource,
                        state,
                        liveSnapshots,
                        liveWriter,
                        history,
                        documents,
                        operations,
                        liveHook,
                        liveValidation),
                new CatalogLiveUndoService(dataSource, state, history, liveSnapshots, liveWriter, liveHook, operations),
                operationalOffers,
                documents);
    }

    public record Services(
            java.util.concurrent.Executor reads,
            JdbcCatalogStudioQueryRepository queries,
            CatalogLiveMutationService liveMutations,
            CatalogLiveChangeSetService liveChangeSets,
            CatalogLiveUndoService liveUndo,
            CatalogOperationalOfferRepository operationalOffers,
            CatalogStudioDocumentService documents) {}
}
