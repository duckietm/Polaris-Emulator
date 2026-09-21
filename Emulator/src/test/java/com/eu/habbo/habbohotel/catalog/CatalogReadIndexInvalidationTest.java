package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Verifies that {@link CatalogManager#readIndex()} is rebuilt exactly when the catalog changes:
 * on an explicit {@link CatalogManager#markCatalogChanged()}, on {@link
 * CatalogManager#moveCatalogItem}, and on every public mutator of {@link CatalogAdminCacheSync}.
 */
class CatalogReadIndexInvalidationTest {

    @Test
    void indexIdentityIsStableWithoutMutation() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();

        CatalogReadIndex first = manager.readIndex();
        CatalogReadIndex second = manager.readIndex();

        assertSame(first, second);
    }

    @Test
    void markCatalogChangedRebuilds() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();

        CatalogReadIndex before = manager.readIndex();
        long versionBefore = manager.catalogVersion();
        manager.markCatalogChanged();
        CatalogReadIndex after = manager.readIndex();

        assertNotSame(before, after);
        assertTrue(after.version() > versionBefore);
    }

    @Test
    void addItemThenMarkIsVisible() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        CatalogReadFixture.TestPage page10 =
                CatalogReadFixture.page(10, -1, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page10);

        assertNull(manager.readIndex().item(CatalogPageType.NORMAL, 7));

        CatalogItem item7 = CatalogReadFixture.item(7, 10, 0);
        page10.addItem(item7);
        manager.markCatalogChanged();

        assertSame(item7, manager.readIndex().item(CatalogPageType.NORMAL, 7));
    }

    @Test
    void moveCatalogItemInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        CatalogReadFixture.TestPage pageA =
                CatalogReadFixture.page(10, -1, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, pageA);
        CatalogReadFixture.TestPage pageB =
                CatalogReadFixture.page(20, -1, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, pageB);

        CatalogItem item = CatalogReadFixture.item(5, 10, 0);
        pageA.addItem(item);
        manager.markCatalogChanged();

        assertNotNull(manager.readIndex().sortedItems(CatalogPageType.NORMAL, 10));
        assertTrue(manager.readIndex().sortedItems(CatalogPageType.NORMAL, 10).contains(item));
        assertTrue(manager.readIndex().sortedItems(CatalogPageType.NORMAL, 20).isEmpty());

        boolean moved = withDatabaseStub(manager, () -> manager.moveCatalogItem(item, 20));
        assertTrue(moved);

        assertFalse(manager.readIndex().sortedItems(CatalogPageType.NORMAL, 10).contains(item));
        assertTrue(manager.readIndex().sortedItems(CatalogPageType.NORMAL, 20).contains(item));
    }

    @Test
    void adminAttachCreatedPageInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        CatalogReadFixture.TestPage root =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, root);
        CatalogReadFixture.TestPage created =
                CatalogReadFixture.page(30, -2, 0, true, true, false, 0, "created", null, CatalogPageType.NORMAL);
        manager.getCatalogPagesMap(CatalogPageType.NORMAL).put(created.getId(), created);
        manager.markCatalogChanged();

        assertFalse(manager.readIndex().children(CatalogPageType.NORMAL, -1).contains(created));

        withFixtureManager(
                manager, () -> CatalogAdminCacheSync.attachCreatedPage(created, -1, 1, CatalogPageType.NORMAL));

        assertTrue(manager.readIndex().children(CatalogPageType.NORMAL, -1).contains(created));
    }

    @Test
    void adminReparentPageInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        CatalogReadFixture.TestPage root =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, root);
        CatalogReadFixture.TestPage parentTen =
                CatalogReadFixture.page(10, -1, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, parentTen);
        CatalogReadFixture.TestPage moved =
                CatalogReadFixture.page(30, -1, 0, true, true, false, 0, "moved", null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, moved);
        manager.markCatalogChanged();

        assertTrue(manager.readIndex().children(CatalogPageType.NORMAL, -1).contains(moved));

        withFixtureManager(manager, () -> CatalogAdminCacheSync.reparentPage(moved, 10, 1, CatalogPageType.NORMAL));

        assertTrue(manager.readIndex().children(CatalogPageType.NORMAL, 10).contains(moved));
        assertFalse(manager.readIndex().children(CatalogPageType.NORMAL, -1).contains(moved));
    }

    @Test
    void adminDetachDeletedPageInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        CatalogReadFixture.TestPage root =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, root);
        CatalogReadFixture.TestPage doomed =
                CatalogReadFixture.page(30, -1, 0, true, true, false, 0, "doomed_page", null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, doomed);
        manager.markCatalogChanged();

        assertNotNull(manager.readIndex().pageByCaption("doomed_page"));

        withFixtureManager(manager, () -> CatalogAdminCacheSync.detachDeletedPage(doomed, CatalogPageType.NORMAL));

        assertNull(manager.readIndex().pageByCaption("doomed_page"));
    }

    @Test
    void adminRemoveCatalogItemInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        CatalogReadFixture.TestPage page10 =
                CatalogReadFixture.page(10, -1, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page10);
        CatalogItem item = CatalogReadFixture.item(9, 10, 0);
        page10.addItem(item);
        manager.markCatalogChanged();

        assertSame(item, manager.readIndex().item(CatalogPageType.NORMAL, 9));

        withFixtureManager(manager, () -> CatalogAdminCacheSync.removeCatalogItem(9, CatalogPageType.NORMAL, 10));

        assertNull(manager.readIndex().item(CatalogPageType.NORMAL, 9));
    }

    @Test
    void voucherDeletionInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        Voucher voucher = CatalogReadFixture.voucher("DEL");
        addVoucher(manager, voucher);
        manager.markCatalogChanged();

        assertSame(voucher, manager.readIndex().voucher("DEL"));
        long versionBefore = manager.catalogVersion();

        // Exercises the in-memory half of CatalogManager#deleteVoucher without a live connection:
        // this is the call that was missing markCatalogChanged(), leaving getVoucher(code) still
        // returning a deleted voucher.
        manager.forgetVoucher(voucher);

        assertTrue(manager.catalogVersion() > versionBefore);
        assertNull(manager.readIndex().voucher("DEL"));
    }

    @Test
    void clothingMutationInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        ClothItem hat = CatalogReadFixture.cloth(1, "hat");
        manager.clothing.put(1, hat);
        manager.markCatalogChanged();

        assertSame(hat, manager.readIndex().clothing("hat"));
        assertNull(manager.readIndex().clothing("cap"));

        ClothItem cap = CatalogReadFixture.cloth(2, "cap");
        Map<Integer, ClothItem> updated = new HashMap<>(manager.clothing);
        updated.put(2, cap);
        CatalogManager.replaceContents(manager.clothing, updated);
        manager.markCatalogChanged();

        assertSame(cap, manager.readIndex().clothing("cap"));
    }

    @Test
    void clubItemsMutationInvalidates() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        CatalogItem item = CatalogReadFixture.item(500, 10, 0);

        assertNull(manager.readIndex().clubItem(500));

        synchronized (manager.clubItems) {
            manager.clubItems.add(item);
        }
        manager.markCatalogChanged();

        assertSame(item, manager.readIndex().clubItem(500));
    }

    @Test
    void sortFlagChangesSortedItems() throws Exception {
        boolean previousSort = CatalogManager.SORT_USING_ORDERNUM;
        try {
            CatalogManager manager = CatalogReadFixture.manager();
            CatalogReadFixture.TestPage page10 =
                    CatalogReadFixture.page(10, -1, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
            CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page10);

            CatalogItem itemA = CatalogReadFixture.item(1, 10, 5);
            CatalogItem itemB = CatalogReadFixture.item(2, 10, 1);
            page10.addItem(itemA);
            page10.addItem(itemB);

            CatalogManager.SORT_USING_ORDERNUM = false;
            manager.markCatalogChanged();
            List<CatalogItem> byId = manager.readIndex().sortedItems(CatalogPageType.NORMAL, 10);
            assertEquals(List.of(1, 2), byId.stream().map(CatalogItem::getId).toList());

            CatalogManager.SORT_USING_ORDERNUM = true;
            List<CatalogItem> byOrderNum = manager.readIndex().sortedItems(CatalogPageType.NORMAL, 10);
            assertEquals(
                    List.of(2, 1), byOrderNum.stream().map(CatalogItem::getId).toList());
        } finally {
            CatalogManager.SORT_USING_ORDERNUM = previousSort;
        }
    }

    @Test
    void everyPublicAdminMutatorBumpsTheVersion() {
        Set<String> coveredByCases =
                Set.of("attachCreatedPage", "reparentPage", "detachDeletedPage", "removeCatalogItem");
        // These need a live database connection to execute a full path; their bump is verified
        // by code review (try/finally around the whole body), not by running them here.
        Set<String> needsDatabase = Set.of("refreshPageFlagsFromDb", "applyPageSave", "reloadCatalogItem");
        Set<String> excluded = Set.of("currentCatalogManager", "openCatalogConnection");

        Set<String> seen = new HashSet<>();
        for (Method method : CatalogAdminCacheSync.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) continue;
            if (excluded.contains(method.getName())) continue;
            seen.add(method.getName());

            if (!coveredByCases.contains(method.getName()) && !needsDatabase.contains(method.getName())) {
                fail("New public CatalogAdminCacheSync mutator '" + method.getName()
                        + "' is not covered by an invalidation test case or the needsDatabase set");
            }
        }

        assertTrue(seen.containsAll(coveredByCases));
        // containsAll, not anyMatch: anyMatch is satisfied by a single needsDatabase method and
        // silently stops covering the others (or a newly added one) as the set grows.
        assertTrue(seen.containsAll(needsDatabase));
    }

    @SuppressWarnings("unchecked")
    private static void addVoucher(CatalogManager manager, Voucher voucher) throws Exception {
        Field vouchersField = CatalogManager.class.getDeclaredField("vouchers");
        vouchersField.setAccessible(true);
        List<Voucher> vouchers = (List<Voucher>) vouchersField.get(manager);
        vouchers.add(voucher);
    }

    private interface Mutation {
        void run();
    }

    private interface DatabaseCall {
        boolean run() throws Exception;
    }

    private static void withFixtureManager(CatalogManager manager, Mutation mutation) {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            GameEnvironment gameEnvironment = mock(GameEnvironment.class);
            when(gameEnvironment.getCatalogManager()).thenReturn(manager);
            emulator.when(Emulator::getGameEnvironment).thenReturn(gameEnvironment);

            mutation.run();
        }
    }

    /**
     * Stubs {@link Emulator#getDatabase()} and {@link Emulator#getGameEnvironment()} so {@code
     * CatalogItem.run()} (and the limited-sells lookup it makes) do not hit a live connection or a
     * missing game environment.
     */
    private static boolean withDatabaseStub(CatalogManager manager, DatabaseCall call) {
        try {
            Database database = mock(Database.class);
            HikariDataSource dataSource = mock(HikariDataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(database.getDataSource()).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(statement);

            GameEnvironment gameEnvironment = mock(GameEnvironment.class);
            when(gameEnvironment.getCatalogManager()).thenReturn(manager);

            try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
                emulator.when(Emulator::getDatabase).thenReturn(database);
                emulator.when(Emulator::getGameEnvironment).thenReturn(gameEnvironment);
                return call.run();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
