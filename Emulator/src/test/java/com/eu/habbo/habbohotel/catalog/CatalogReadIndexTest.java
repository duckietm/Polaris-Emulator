package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.users.Habbo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Characterizes the current, unindexed catalog lookups on {@link CatalogManager} before the
 * read-index refactor touches them. Every case runs the production method and a verbatim copy of
 * its current body ({@code scanX}) against the same manager and asserts they agree, so the
 * refactor has a DB-free baseline to keep matching.
 */
class CatalogReadIndexTest {

    @Test
    void itemByIdMatchesScan() throws Exception {
        CatalogManager manager = buildManager();

        assertSame(
                scanCatalogItem(manager, 1, CatalogPageType.NORMAL), manager.getCatalogItem(1, CatalogPageType.NORMAL));
        assertSame(
                scanCatalogItem(manager, 2, CatalogPageType.NORMAL), manager.getCatalogItem(2, CatalogPageType.NORMAL));
        assertSame(
                scanCatalogItem(manager, 3, CatalogPageType.NORMAL), manager.getCatalogItem(3, CatalogPageType.NORMAL));
        assertSame(
                scanCatalogItem(manager, 200, CatalogPageType.BUILDER),
                manager.getCatalogItem(200, CatalogPageType.BUILDER));
        assertSame(
                scanCatalogItem(manager, 999, CatalogPageType.NORMAL),
                manager.getCatalogItem(999, CatalogPageType.NORMAL));
        assertSame(
                scanCatalogItem(manager, 1, CatalogPageType.BUILDER),
                manager.getCatalogItem(1, CatalogPageType.BUILDER));
    }

    @Test
    void clothingMatchesScan() throws Exception {
        CatalogManager manager = buildManager();

        for (String name : new String[] {"cool_hat", "COOL_HAT", "sunglasses", "missing", null}) {
            assertSame(scanClothing(manager, name), manager.getClothing(name));
        }
    }

    @Test
    void voucherMatchesScan() throws Exception {
        CatalogManager manager = buildManager();

        for (String code : new String[] {"ABC", "abc", "x"}) {
            assertSame(scanVoucher(manager, code), manager.getVoucher(code));
        }
    }

    @Test
    void clubItemMatchesScan() throws Exception {
        CatalogManager manager = buildManager();

        assertSame(scanClubItem(manager, 2), manager.getClubItem(2));
        assertSame(scanClubItem(manager, 1), manager.getClubItem(1));
    }

    @Test
    void pageByCaptionMatchesScan() throws Exception {
        CatalogManager manager = buildManager();

        assertSame(scanCatalogPage(manager, "ROOM_BUNDLE_5"), manager.getCatalogPage("ROOM_BUNDLE_5"));
        assertSame(scanCatalogPage(manager, "nope"), manager.getCatalogPage("nope"));
    }

    @Test
    void pageByLayoutMatchesScan() throws Exception {
        CatalogManager manager = buildManager();

        assertSame(scanCatalogPageByLayout(manager, "CLUB_GIFT"), manager.getCatalogPageByLayout("CLUB_GIFT"));
        assertSame(scanCatalogPageByLayout(manager, "none"), manager.getCatalogPageByLayout("none"));
    }

    @Test
    void childrenMatchScanForEveryUser() throws Exception {
        CatalogManager manager = buildManager();
        int[] parents = {-1, 10};
        CatalogPageType[] types = {CatalogPageType.NORMAL, CatalogPageType.BUILDER};
        int[] ranks = {0, 5};
        boolean[] clubs = {false, true};

        for (int parentId : parents) {
            for (CatalogPageType type : types) {
                for (int rank : ranks) {
                    for (boolean club : clubs) {
                        Habbo habbo = CatalogReadFixture.habbo(rank, club);

                        List<CatalogPage> expected = scanCatalogPages(manager, parentId, habbo, type);
                        List<CatalogPage> actual = manager.getCatalogPages(parentId, habbo, type);

                        assertEquals(expected, actual);
                        assertEquals(
                                expected.stream().map(CatalogPage::getId).toList(),
                                actual.stream().map(CatalogPage::getId).toList());
                    }
                }
            }
        }
    }

    @Test
    void effectivePageItemsMatchScan() throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        boolean previousSort = CatalogManager.SORT_USING_ORDERNUM;
        try {
            configField.set(
                    null,
                    new ConfigurationManager(Path.of("..", "config example", "config.ini.example")
                            .toString()));
            CatalogManager manager = buildManager();
            CatalogPage page10 = manager.getCatalogPage(10, CatalogPageType.NORMAL);

            CatalogManager.SORT_USING_ORDERNUM = false;
            assertEffectivePageItemsMatchAsSet(manager, page10);

            CatalogManager.SORT_USING_ORDERNUM = true;
            assertEffectivePageItemsMatchAsSet(manager, page10);
        } finally {
            CatalogManager.SORT_USING_ORDERNUM = previousSort;
            configField.set(null, previousConfig);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Fixture assembly
    // ----------------------------------------------------------------------------------------

    private static CatalogManager buildManager() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();

        CatalogReadFixture.TestPage normalRoot =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, normalRoot);

        CatalogReadFixture.TestPage builderRoot =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, null, null, CatalogPageType.BUILDER);
        CatalogReadFixture.attach(manager, CatalogPageType.BUILDER, builderRoot);

        CatalogReadFixture.TestPage page10 =
                CatalogReadFixture.page(10, -1, 0, true, true, false, 0, null, "club_gift", CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page10);

        CatalogReadFixture.TestPage page20 =
                CatalogReadFixture.page(20, -1, 0, true, true, false, 5, "room_bundle_5", null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page20);

        CatalogReadFixture.TestPage page30 =
                CatalogReadFixture.page(30, -1, 0, true, true, true, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page30);

        CatalogReadFixture.TestPage page40 =
                CatalogReadFixture.page(40, -1, 0, false, true, false, 0, null, "club_gift", CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page40);

        CatalogReadFixture.TestPage page50 =
                CatalogReadFixture.page(50, -1, 0, true, true, false, 0, null, null, CatalogPageType.BOTH);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page50);

        CatalogReadFixture.TestPage page11 =
                CatalogReadFixture.page(11, 10, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page11);

        CatalogReadFixture.TestPage page100 =
                CatalogReadFixture.page(100, -1, 0, true, true, false, 0, null, null, CatalogPageType.BUILDER);
        CatalogReadFixture.attach(manager, CatalogPageType.BUILDER, page100);

        CatalogReadFixture.TestPage page110 =
                CatalogReadFixture.page(110, 100, 0, true, true, false, 0, null, null, CatalogPageType.BUILDER);
        CatalogReadFixture.attach(manager, CatalogPageType.BUILDER, page110);

        CatalogItem item1OnPage10 = CatalogReadFixture.item(1, 10, 3);
        page10.addItem(item1OnPage10);
        CatalogItem item2 = CatalogReadFixture.item(2, 10, 1);
        page10.addItem(item2);
        manager.clubItems.add(item2);
        CatalogItem item3 = CatalogReadFixture.item(3, 11, 2);
        page11.addItem(item3);
        CatalogItem item1OnPage20 = CatalogReadFixture.item(1, 20, 0);
        page20.addItem(item1OnPage20);
        CatalogItem item200 = CatalogReadFixture.item(200, 100, 0);
        page100.addItem(item200);

        manager.clothing.put(1, CatalogReadFixture.cloth(1, "Cool_Hat"));
        manager.clothing.put(2, CatalogReadFixture.cloth(2, "sunglasses"));

        addVoucher(manager, CatalogReadFixture.voucher("ABC"));
        addVoucher(manager, CatalogReadFixture.voucher("abc"));

        return manager;
    }

    @SuppressWarnings("unchecked")
    private static void addVoucher(CatalogManager manager, Voucher voucher) throws Exception {
        Field vouchersField = CatalogManager.class.getDeclaredField("vouchers");
        vouchersField.setAccessible(true);
        List<Voucher> vouchers = (List<Voucher>) vouchersField.get(manager);
        vouchers.add(voucher);
    }

    // ----------------------------------------------------------------------------------------
    // Reference copies - verbatim bodies of the current CatalogManager lookups, taken before
    // the read-index refactor (Task 3) changes them.
    // ----------------------------------------------------------------------------------------

    private static CatalogItem scanCatalogItem(CatalogManager manager, int id, CatalogPageType pageType) {
        final CatalogItem[] item = {null};
        final Int2ObjectMap<CatalogPage> pagesMap = manager.getCatalogPagesMap(pageType);

        synchronized (pagesMap) {
            for (CatalogPage object : pagesMap.values()) {
                item[0] = object.getCatalogItem(id);
                if (item[0] != null) {
                    break;
                }
            }
        }

        return item[0];
    }

    private static ClothItem scanClothing(CatalogManager manager, String name) {
        synchronized (manager.clothing) {
            for (ClothItem item : manager.clothing.values()) {
                if (item.name.equalsIgnoreCase(name)) {
                    return item;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Voucher scanVoucher(CatalogManager manager, String code) throws Exception {
        Field vouchersField = CatalogManager.class.getDeclaredField("vouchers");
        vouchersField.setAccessible(true);
        List<Voucher> vouchers = (List<Voucher>) vouchersField.get(manager);

        synchronized (vouchers) {
            for (Voucher voucher : vouchers) {
                if (voucher.code.equals(code)) {
                    return voucher;
                }
            }
        }
        return null;
    }

    private static CatalogItem scanClubItem(CatalogManager manager, int itemId) {
        synchronized (manager.clubItems) {
            for (CatalogItem item : manager.clubItems) {
                if (item.getId() == itemId) return item;
            }
        }

        return null;
    }

    private static CatalogPage scanCatalogPage(CatalogManager manager, String captionSafe) {
        return manager.catalogPages.values().stream()
                .filter(p ->
                        p != null && p.getPageName() != null && p.getPageName().equalsIgnoreCase(captionSafe))
                .findAny()
                .orElse(null);
    }

    private static CatalogPage scanCatalogPageByLayout(CatalogManager manager, String layoutName) {
        return manager.catalogPages.values().stream()
                .filter(p -> p != null
                        && p.isVisible()
                        && p.isEnabled()
                        && p.getRank() < 2
                        && p.getLayout() != null
                        && p.getLayout().equalsIgnoreCase(layoutName))
                .findAny()
                .orElse(null);
    }

    private static List<CatalogPage> scanCatalogPages(
            CatalogManager manager, int parentId, final Habbo habbo, final CatalogPageType pageType) {
        final List<CatalogPage> pages = new ArrayList<>();
        final Int2ObjectMap<CatalogPage> pagesMap = manager.getCatalogPagesMap(pageType);
        CatalogPage parentPage = pagesMap.get(parentId);

        if (parentPage == null) {
            return pages;
        }

        for (CatalogPage object : parentPage.childPages.values()) {
            boolean isVisiblePage = object.visible;
            boolean hasRightRank =
                    object.getRank() <= habbo.getHabboInfo().getRank().getId();
            boolean clubRightsOkay =
                    !object.isClubOnly() || habbo.getHabboInfo().getHabboStats().hasActiveClub();
            boolean pageTypeMatches = (pageType == CatalogPageType.BUILDER)
                    || object.getCatalogPageType().matches(pageType);

            if (isVisiblePage && hasRightRank && clubRightsOkay && pageTypeMatches) {
                pages.add(object);
            }
        }
        Collections.sort(pages);

        return pages;
    }

    private static List<CatalogItem> scanEffectivePageItems(CatalogManager manager, CatalogPage page) {
        List<CatalogItem> items = new ArrayList<>(page.getCatalogItems().values());

        int soldOutPageId = Emulator.getConfig().getInt("catalog.ltd.page.soldout");
        if (soldOutPageId <= 0) {
            return items;
        }

        if (page.getId() == soldOutPageId) {
            synchronized (manager.limitedNumbers) {
                for (Map.Entry<Integer, CatalogLimitedConfiguration> entry : manager.limitedNumbers.entrySet()) {
                    if (entry.getValue().available() != 0) {
                        continue;
                    }

                    CatalogItem soldOut = manager.getCatalogItem(entry.getKey());
                    if (soldOut != null && soldOut.getPageId() != soldOutPageId && !items.contains(soldOut)) {
                        items.add(soldOut);
                    }
                }
            }
        } else {
            items.removeIf(manager::isLimitedSoldOut);
        }

        return items;
    }

    /**
     * The reference scan returns items in an unspecified order while the read-index-backed method
     * returns them sorted; the contract is the set of items, not their order. Also asserts the
     * indexed result is actually sorted by {@link CatalogItem#compareTo}.
     */
    private static void assertEffectivePageItemsMatchAsSet(CatalogManager manager, CatalogPage page) {
        List<CatalogItem> expected = scanEffectivePageItems(manager, page);
        List<CatalogItem> actual = manager.getEffectivePageItems(page);

        Set<CatalogItem> expectedSet = new HashSet<>(expected);
        Set<CatalogItem> actualSet = new HashSet<>(actual);
        assertEquals(expectedSet, actualSet);
        assertEquals(expected.size(), actual.size());

        List<CatalogItem> sorted = new ArrayList<>(actual);
        Collections.sort(sorted);
        assertEquals(sorted, actual, "getEffectivePageItems should return items sorted");
    }
}
