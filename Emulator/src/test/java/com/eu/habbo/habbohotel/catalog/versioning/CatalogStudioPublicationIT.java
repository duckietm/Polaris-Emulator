package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CatalogStudioPublicationIT {
    private static final int ACTOR_ID = 7;

    @Test
    void publicationReloadsOperationalOffersAndArchivedVersionsCanBeRestored() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("catalog_studio_publication")) {
            MigrationRunner.migrate(dataSource);

            Gson gson = new Gson();
            JdbcCatalogVersionRepository versions = new JdbcCatalogVersionRepository();
            JdbcCatalogLockRepository locks = new JdbcCatalogLockRepository(dataSource);
            JdbcCatalogChangeJournal journal = new JdbcCatalogChangeJournal();
            JdbcCatalogSnapshotWriter snapshotWriter = new JdbcCatalogSnapshotWriter(gson);
            CatalogAdminDraftMutationService mutations =
                    new CatalogAdminDraftMutationService(dataSource, versions, journal, snapshotWriter, locks, gson);
            CatalogUndoService undo = new CatalogUndoService(dataSource, versions, journal, snapshotWriter, gson);
            CatalogRuntimeState initialState = runtimeState(dataSource, versions);
            CatalogVersionSnapshot initialDraft = snapshot(dataSource, versions, initialState.draftVersionId());
            CatalogPageSnapshot originalPage = initialDraft.pages().stream()
                    .filter(page -> page.catalogType() == CatalogPageType.NORMAL)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The migrated catalog must contain a normal page"));
            assertOperationalPageBooleans(dataSource, originalPage);
            CatalogOfferSnapshot original = initialDraft.offers().stream()
                    .filter(offer -> offer.catalogType() == CatalogPageType.NORMAL)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The migrated catalog must contain a normal offer"));
            int originalLivePrice = operationalPrice(dataSource, original.offerId());
            int publishedPrice = originalLivePrice + 17;

            CatalogLockKey offerKey = new CatalogLockKey(CatalogEntityType.OFFER, original.offerId());
            CatalogLockResult acquired = new CatalogLockService(locks)
                    .acquire(initialState.draftVersionId(), offerKey, ACTOR_ID, Duration.ofSeconds(90));
            assertTrue(acquired.acquired());
            CatalogOfferSnapshot edited = withCredits(original, publishedPrice);
            CatalogDraftMutationResult mutation = mutations.apply(new CatalogDraftMutationRequest(
                    initialState.draftVersionId(),
                    initialDraft.version().revision(),
                    ACTOR_ID,
                    offerKey,
                    acquired.token().orElseThrow(),
                    "Integration price edit",
                    CatalogEntityType.OFFER,
                    original.offerId(),
                    CatalogChangeOperation.UPDATE,
                    gson.toJson(edited)));

            assertEquals(
                    originalLivePrice,
                    operationalPrice(dataSource, original.offerId()),
                    "draft edits must not leak into the live catalog");

            AtomicInteger reloads = new AtomicInteger();
            AtomicReference<CatalogVersionSnapshot> cachedCatalog = new AtomicReference<>();
            CatalogPublicationService publication = new CatalogPublicationService(
                    dataSource,
                    versions,
                    new JdbcCatalogValidationDataRepository(),
                    new JdbcCatalogLiveProjection(),
                    locks,
                    (published, nextDraftId) -> {
                        cachedCatalog.set(published);
                        reloads.incrementAndGet();
                    });
            CatalogPublicationResult published = publication.publish(new CatalogPublicationRequest(
                    initialState.draftVersionId(), mutation.revision(), ACTOR_ID, "Draft after integration publish"));

            assertTrue(published.published());
            assertEquals(1, reloads.get(), "the catalog cache hook must run exactly once after commit");
            assertEquals(
                    publishedPrice,
                    cachedCatalog
                            .get()
                            .offer(CatalogPageType.NORMAL, original.offerId())
                            .orElseThrow()
                            .costCredits());
            assertEquals(
                    publishedPrice,
                    purchaseVisibleOffer(dataSource, original.offerId()).getCredits(),
                    "the runtime purchase model must read the newly published price");
            assertOperationalPageBooleans(dataSource, originalPage);

            CatalogRuntimeState afterPublish = runtimeState(dataSource, versions);
            assertTrue(
                    new JdbcCatalogStudioQueryRepository(dataSource)
                            .loadSession().publishedVersions().stream()
                                    .anyMatch(version -> version.id() == initialState.activeVersionId()),
                    "the previous publication must remain selectable for restore");

            long restoreRevision =
                    undo.restore(afterPublish.draftVersionId(), 0, initialState.activeVersionId(), ACTOR_ID);
            CatalogPublicationResult restored = publication.publish(new CatalogPublicationRequest(
                    afterPublish.draftVersionId(), restoreRevision, ACTOR_ID, "Draft after integration restore"));

            assertTrue(restored.published());
            assertEquals(2, reloads.get());
            assertEquals(
                    originalLivePrice,
                    purchaseVisibleOffer(dataSource, original.offerId()).getCredits(),
                    "publishing a restored version must restore the operational purchase price");
        }
    }

    private static CatalogRuntimeState runtimeState(HikariDataSource dataSource, JdbcCatalogVersionRepository versions)
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                return versions.lockRuntimeState(connection);
            } finally {
                connection.rollback();
            }
        }
    }

    private static CatalogVersionSnapshot snapshot(
            HikariDataSource dataSource, JdbcCatalogVersionRepository versions, long versionId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return versions.loadSnapshot(connection, versionId);
        }
    }

    private static int operationalPrice(HikariDataSource dataSource, int offerId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT cost_credits FROM catalog_items WHERE id = ?")) {
            statement.setInt(1, offerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new AssertionError("Operational catalog offer is missing: " + offerId);
                return resultSet.getInt("cost_credits");
            }
        }
    }

    private static CatalogItem purchaseVisibleOffer(HikariDataSource dataSource, int offerId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM catalog_items WHERE id = ?")) {
            statement.setInt(1, offerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new AssertionError("Operational catalog offer is missing: " + offerId);
                return new CatalogItem(resultSet);
            }
        }
    }

    private static void assertOperationalPageBooleans(HikariDataSource dataSource, CatalogPageSnapshot page)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT visible, enabled, club_only, vip_only FROM catalog_pages WHERE id = ?")) {
            statement.setInt(1, page.pageId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next())
                    throw new AssertionError("Operational catalog page is missing: " + page.pageId());
                assertEquals(page.visible() ? "1" : "0", resultSet.getString("visible"));
                assertEquals(page.enabled() ? "1" : "0", resultSet.getString("enabled"));
                assertEquals(page.clubOnly() ? "1" : "0", resultSet.getString("club_only"));
                assertEquals(page.vipOnly() ? "1" : "0", resultSet.getString("vip_only"));
            }
        }
    }

    private static CatalogOfferSnapshot withCredits(CatalogOfferSnapshot offer, int credits) {
        return new CatalogOfferSnapshot(
                offer.catalogType(),
                offer.offerId(),
                offer.itemIds(),
                offer.pageId(),
                offer.catalogName(),
                credits,
                offer.costPoints(),
                offer.pointsType(),
                offer.amount(),
                offer.limitedStack(),
                offer.orderNumber(),
                offer.offerIdClient(),
                offer.songId(),
                offer.extradata(),
                offer.haveOffer(),
                offer.clubOnly());
    }

    private static void requireDocker() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI");
            }
            assumeTrue(false, "Docker/Testcontainers not available - skipping Catalog Studio integration test");
        }
    }
}
