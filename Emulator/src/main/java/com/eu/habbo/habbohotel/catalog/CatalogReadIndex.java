package com.eu.habbo.habbohotel.catalog;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A point-in-time, read-only snapshot of the catalog's lookup structures. Built once per {@link
 * CatalogManager#catalogVersion()} value and served from {@link CatalogManager#readIndex()};
 * never mutated after construction, so it can be read without holding any lock. Because it is a
 * snapshot, an entry removed from the catalog after this instance was built (a deleted page,
 * item, voucher, cloth item or club item) stays reachable through it until the next {@link
 * CatalogManager#markCatalogChanged()} forces a rebuild.
 */
final class CatalogReadIndex {

    private final long version;
    private final boolean sortUsingOrderNum;
    private final Map<CatalogPageType, Int2ObjectMap<CatalogItem>> itemsById;
    private final Map<String, ClothItem> clothingByName;
    private final Map<String, Voucher> vouchersByCode;
    private final Int2ObjectMap<CatalogItem> clubItemsById;
    private final Map<String, CatalogPage> pagesByCaptionSafe;
    private final Map<String, CatalogPage> pagesByLayout;
    private final Map<CatalogPageType, Int2ObjectMap<List<CatalogPage>>> childrenByParent;
    private final Map<CatalogPageType, Int2ObjectMap<List<CatalogItem>>> sortedItemsByPage;

    private CatalogReadIndex(
            long version,
            boolean sortUsingOrderNum,
            Map<CatalogPageType, Int2ObjectMap<CatalogItem>> itemsById,
            Map<String, ClothItem> clothingByName,
            Map<String, Voucher> vouchersByCode,
            Int2ObjectMap<CatalogItem> clubItemsById,
            Map<String, CatalogPage> pagesByCaptionSafe,
            Map<String, CatalogPage> pagesByLayout,
            Map<CatalogPageType, Int2ObjectMap<List<CatalogPage>>> childrenByParent,
            Map<CatalogPageType, Int2ObjectMap<List<CatalogItem>>> sortedItemsByPage) {
        this.version = version;
        this.sortUsingOrderNum = sortUsingOrderNum;
        this.itemsById = Collections.unmodifiableMap(unmodifiableValues(itemsById));
        this.clothingByName = Collections.unmodifiableMap(clothingByName);
        this.vouchersByCode = Collections.unmodifiableMap(vouchersByCode);
        this.clubItemsById = Int2ObjectMaps.unmodifiable(clubItemsById);
        this.pagesByCaptionSafe = Collections.unmodifiableMap(pagesByCaptionSafe);
        this.pagesByLayout = Collections.unmodifiableMap(pagesByLayout);
        this.childrenByParent = Collections.unmodifiableMap(unmodifiableValues(childrenByParent));
        this.sortedItemsByPage = Collections.unmodifiableMap(unmodifiableValues(sortedItemsByPage));
    }

    private static <V> Map<CatalogPageType, Int2ObjectMap<V>> unmodifiableValues(
            Map<CatalogPageType, Int2ObjectMap<V>> source) {
        Map<CatalogPageType, Int2ObjectMap<V>> wrapped = new EnumMap<>(CatalogPageType.class);
        for (Map.Entry<CatalogPageType, Int2ObjectMap<V>> entry : source.entrySet()) {
            wrapped.put(entry.getKey(), Int2ObjectMaps.unmodifiable(entry.getValue()));
        }
        return wrapped;
    }

    static CatalogReadIndex build(CatalogManager manager, long version) {
        boolean sortUsingOrderNum = CatalogManager.SORT_USING_ORDERNUM;
        Map<CatalogPageType, Int2ObjectMap<CatalogItem>> itemsById = new EnumMap<>(CatalogPageType.class);
        Map<CatalogPageType, Int2ObjectMap<List<CatalogPage>>> childrenByParent = new EnumMap<>(CatalogPageType.class);
        Map<CatalogPageType, Int2ObjectMap<List<CatalogItem>>> sortedItemsByPage = new EnumMap<>(CatalogPageType.class);
        Map<String, CatalogPage> pagesByCaptionSafe = new HashMap<>();
        Map<String, CatalogPage> pagesByLayout = new HashMap<>();

        for (CatalogPageType type : new CatalogPageType[] {CatalogPageType.NORMAL, CatalogPageType.BUILDER}) {
            Int2ObjectMap<CatalogPage> pages = manager.getCatalogPagesMap(type);
            Int2ObjectMap<CatalogItem> items = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<List<CatalogPage>> children = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<List<CatalogItem>> sortedItems = new Int2ObjectOpenHashMap<>();
            synchronized (pages) {
                for (CatalogPage page : pages.values()) {
                    if (page == null) continue;

                    // catalogItemsView(), not the overridable getCatalogItems(): RoomBundleLayout
                    // overrides getCatalogItems() to recompute the bundle from the room (DB I/O)
                    // as a side effect, which must not run for every bundle page on every build
                    // while this loop holds `pages` and the manager's readIndexLock.
                    Int2ObjectMap<CatalogItem> pageItems = page.catalogItemsView();
                    List<CatalogItem> pageItemList;
                    synchronized (pageItems) {
                        for (Int2ObjectMap.Entry<CatalogItem> entry : pageItems.int2ObjectEntrySet()) {
                            items.putIfAbsent(entry.getIntKey(), entry.getValue());
                        }
                        pageItemList = new ArrayList<>(pageItems.values());
                    }
                    Collections.sort(pageItemList);
                    // Keyed by page.getId(), which equals the map key it was stored under by
                    // construction (loadCatalogPages/loadBuildersClubCatalogPages,
                    // createCatalogPage and CatalogRootLayout all insert a page under its own id).
                    sortedItems.put(page.getId(), Collections.unmodifiableList(pageItemList));

                    List<CatalogPage> pageChildren =
                            new ArrayList<>(page.getChildPages().values());
                    Collections.sort(pageChildren);
                    // Same invariant as sortedItems above: keyed by page.getId().
                    children.put(page.getId(), Collections.unmodifiableList(pageChildren));

                    if (type == CatalogPageType.NORMAL) {
                        // Keys are lower-cased with toLowerCase(Locale.ROOT) rather than compared
                        // with equalsIgnoreCase at lookup time: the snapshot is built once and
                        // read many times, so paying the normalisation cost here, on a HashMap
                        // key, is cheaper than a per-lookup case-insensitive scan.
                        if (page.getPageName() != null) {
                            pagesByCaptionSafe.putIfAbsent(page.getPageName().toLowerCase(Locale.ROOT), page);
                        }
                        if (page.isVisible() && page.isEnabled() && page.getRank() < 2 && page.getLayout() != null) {
                            pagesByLayout.putIfAbsent(page.getLayout().toLowerCase(Locale.ROOT), page);
                        }
                    }
                }
            }
            itemsById.put(type, items);
            childrenByParent.put(type, children);
            sortedItemsByPage.put(type, sortedItems);
        }

        Map<String, ClothItem> clothingByName = new HashMap<>();
        synchronized (manager.clothing) {
            for (ClothItem item : manager.clothing.values()) {
                if (item.name != null) {
                    clothingByName.putIfAbsent(item.name.toLowerCase(Locale.ROOT), item);
                }
            }
        }

        Map<String, Voucher> vouchersByCode = new HashMap<>();
        List<Voucher> vouchers = manager.vouchersView();
        synchronized (vouchers) {
            for (Voucher voucher : vouchers) {
                vouchersByCode.putIfAbsent(voucher.code, voucher);
            }
        }

        Int2ObjectMap<CatalogItem> clubItemsById = new Int2ObjectOpenHashMap<>();
        synchronized (manager.clubItems) {
            for (CatalogItem item : manager.clubItems) {
                clubItemsById.putIfAbsent(item.getId(), item);
            }
        }

        return new CatalogReadIndex(
                version,
                sortUsingOrderNum,
                itemsById,
                clothingByName,
                vouchersByCode,
                clubItemsById,
                pagesByCaptionSafe,
                pagesByLayout,
                childrenByParent,
                sortedItemsByPage);
    }

    long version() {
        return this.version;
    }

    boolean sortUsingOrderNum() {
        return this.sortUsingOrderNum;
    }

    CatalogItem item(CatalogPageType type, int id) {
        Int2ObjectMap<CatalogItem> items = this.itemsById.get(type);
        return items == null ? null : items.get(id);
    }

    ClothItem clothing(String name) {
        return name == null ? null : this.clothingByName.get(name.toLowerCase(Locale.ROOT));
    }

    Voucher voucher(String code) {
        return code == null ? null : this.vouchersByCode.get(code);
    }

    CatalogItem clubItem(int id) {
        return this.clubItemsById.get(id);
    }

    CatalogPage pageByCaption(String captionSafe) {
        return captionSafe == null ? null : this.pagesByCaptionSafe.get(captionSafe.toLowerCase(Locale.ROOT));
    }

    CatalogPage pageByLayout(String layout) {
        return layout == null ? null : this.pagesByLayout.get(layout.toLowerCase(Locale.ROOT));
    }

    List<CatalogPage> children(CatalogPageType type, int parentId) {
        Int2ObjectMap<List<CatalogPage>> children = this.childrenByParent.get(type);
        if (children == null) return Collections.emptyList();
        List<CatalogPage> found = children.get(parentId);
        return found == null ? Collections.emptyList() : found;
    }

    List<CatalogItem> sortedItems(CatalogPageType type, int pageId) {
        Int2ObjectMap<List<CatalogItem>> sortedItems = this.sortedItemsByPage.get(type);
        return sortedItems == null ? null : sortedItems.get(pageId);
    }
}
