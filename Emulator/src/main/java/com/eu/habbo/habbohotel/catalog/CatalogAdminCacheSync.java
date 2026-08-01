package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the in-memory catalog cache aligned with catalog admin DB mutations
 * (draft edits before publish reload).
 */
public final class CatalogAdminCacheSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogAdminCacheSync.class);

    private static final String BC_ITEM_SELECT =
            "SELECT id, item_ids, page_id, catalog_name, 0 AS cost_credits, 0 AS cost_points, 0 AS points_type, 1 AS amount, "
                    + "0 AS limited_stack, 0 AS limited_sells, extradata, '0' AS club_only, '1' AS have_offer, id AS offer_id, order_number "
                    + "FROM catalog_items_bc WHERE id = ? LIMIT 1";

    private CatalogAdminCacheSync() {}

    public static CatalogManager currentCatalogManager() {
        return Emulator.getGameEnvironment().getCatalogManager();
    }

    public static Connection openCatalogConnection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    public static void attachCreatedPage(CatalogPage page, int parentId, int orderNum, CatalogPageType pageType) {
        if (page == null) return;
        reparentPage(page, parentId, orderNum, pageType);
    }

    public static void reparentPage(CatalogPage page, int newParentId, int newOrderNum, CatalogPageType pageType) {
        if (page == null) return;

        CatalogManager catalogManager = currentCatalogManager();
        int oldParentId = page.getParentId();

        if (oldParentId != newParentId) {
            CatalogPage oldParent = catalogManager.getCatalogPage(oldParentId, pageType);
            if (oldParent != null) {
                oldParent.getChildPages().remove(page.getId());
            }

            CatalogPage newParent = catalogManager.getCatalogPage(newParentId, pageType);
            if (newParent != null) {
                newParent.addChildPage(page);
            }

            page.setParentId(newParentId);
        }

        page.setOrderNum(newOrderNum);
    }

    public static void refreshPageFlagsFromDb(int pageId, CatalogPageType pageType) {
        CatalogManager catalogManager = currentCatalogManager();
        CatalogPage page = catalogManager.getCatalogPage(pageId, pageType);
        if (page == null) return;

        String tableName = (pageType == CatalogPageType.BUILDER) ? "catalog_pages_bc" : "catalog_pages";
        String sql = "SELECT visible, enabled FROM " + tableName + " WHERE id = ? LIMIT 1";

        try (Connection connection = openCatalogConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, pageId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return;

                page.setVisible("1".equals(set.getString("visible")) || set.getBoolean("visible"));
                page.setEnabled("1".equals(set.getString("enabled")) || set.getBoolean("enabled"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to refresh catalog page flags for page {}", pageId, e);
        }
    }

    public static void applyPageSave(
            CatalogPage page,
            String caption,
            String captionSave,
            String layout,
            int iconImage,
            int minRank,
            boolean visible,
            boolean enabled,
            int orderNum,
            int parentId,
            String headline,
            String teaser,
            String textDetails,
            String textOne,
            CatalogPageType catalogMode,
            CatalogPageType pageType) {
        if (page == null) return;
        applyPageSave(
                page,
                caption,
                captionSave,
                layout,
                iconImage,
                page.getIconColor(),
                minRank,
                visible,
                enabled,
                page.isClubOnly(),
                page.isVipOnly(),
                orderNum,
                parentId,
                headline,
                teaser,
                page.getSpecialImage(),
                textDetails,
                textOne,
                page.getTextTwo(),
                page.getTextTeaser(),
                page.getRoomId(),
                page.getIncluded().stream().map(String::valueOf).collect(Collectors.joining(";")),
                catalogMode,
                pageType);
    }

    public static void applyPageSave(
            CatalogPage page,
            String caption,
            String captionSave,
            String layout,
            int iconImage,
            int iconColor,
            int minRank,
            boolean visible,
            boolean enabled,
            boolean clubOnly,
            boolean vipOnly,
            int orderNum,
            int parentId,
            String headline,
            String teaser,
            String special,
            String textDetails,
            String textOne,
            String textTwo,
            String textTeaser,
            int roomId,
            String includes,
            CatalogPageType catalogMode,
            CatalogPageType pageType) {
        if (page == null) return;

        if (page.getParentId() != parentId) {
            reparentPage(page, parentId, orderNum, pageType);
        } else {
            page.setOrderNum(orderNum);
        }

        page.setCaption(caption);
        page.setPageName(captionSave);
        page.setLayout(layout);
        page.setIconImage(iconImage);
        page.setIconColor(iconColor);
        page.setRank(minRank);
        page.setVisible(visible);
        page.setEnabled(enabled);
        page.setClubOnly(clubOnly);
        page.setVipOnly(vipOnly);
        page.setHeaderImage(headline);
        page.setTeaserImage(teaser);
        page.setSpecialImage(special);
        page.setTextDetails(textDetails);
        page.setTextOne(textOne);
        page.setTextTwo(textTwo);
        page.setTextTeaser(textTeaser);
        page.setRoomId(roomId);
        page.setIncluded(includes);

        if (pageType != CatalogPageType.BUILDER) {
            page.setCatalogPageType(catalogMode);
        }
    }

    public static void detachDeletedPage(CatalogPage page, CatalogPageType pageType) {
        if (page == null) return;

        CatalogManager catalogManager = currentCatalogManager();
        CatalogPage parent = catalogManager.getCatalogPage(page.getParentId(), pageType);

        if (parent != null) {
            parent.getChildPages().remove(page.getId());
        }

        catalogManager.getCatalogPagesMap(pageType).remove(page.getId());
    }

    public static boolean reloadCatalogItem(int offerId, CatalogPageType pageType) {
        CatalogManager catalogManager = currentCatalogManager();
        CatalogItem existing = catalogManager.getCatalogItem(offerId, pageType);
        int previousPageId = existing != null ? existing.getPageId() : -1;

        String sql = (pageType == CatalogPageType.BUILDER)
                ? BC_ITEM_SELECT
                : "SELECT * FROM catalog_items WHERE id = ? LIMIT 1";

        try (Connection connection = openCatalogConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offerId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    removeCatalogItem(offerId, pageType, previousPageId);
                    return false;
                }

                if (existing != null) {
                    unregisterOfferSearchIndex(existing, pageType);

                    if (previousPageId != set.getInt("page_id")) {
                        CatalogPage oldPage = catalogManager.getCatalogPage(previousPageId, pageType);
                        if (oldPage != null) {
                            oldPage.getCatalogItems().remove(offerId);
                        }
                    }

                    existing.update(set);
                    attachItemToPage(existing, pageType);
                    registerOfferSearchIndex(existing, pageType);
                    syncLimitedConfiguration(existing, pageType, catalogManager);
                    return true;
                }

                if ("0".equals(set.getString("item_ids"))) {
                    return false;
                }

                CatalogItem created = new CatalogItem(set);
                attachItemToPage(created, pageType);
                registerOfferSearchIndex(created, pageType);
                syncLimitedConfiguration(created, pageType, catalogManager);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to reload catalog item {}", offerId, e);
            return false;
        }
    }

    private static void syncLimitedConfiguration(
            CatalogItem item, CatalogPageType pageType, CatalogManager catalogManager) {
        if (item == null || pageType == CatalogPageType.BUILDER || !item.isLimited()) return;
        catalogManager.createOrUpdateLimitedConfig(item);
    }

    public static void removeCatalogItem(int offerId, CatalogPageType pageType, int pageIdHint) {
        CatalogManager catalogManager = currentCatalogManager();
        CatalogItem item = catalogManager.getCatalogItem(offerId, pageType);

        if (item != null) {
            unregisterOfferSearchIndex(item, pageType);
            if (pageType != CatalogPageType.BUILDER) {
                synchronized (catalogManager.limitedNumbers) {
                    catalogManager.limitedNumbers.remove(offerId);
                }
            }
        }

        int pageId = item != null ? item.getPageId() : pageIdHint;

        if (pageId > -1) {
            CatalogPage page = catalogManager.getCatalogPage(pageId, pageType);
            if (page != null) {
                page.getCatalogItems().remove(offerId);
            }
        }
    }

    private static void unregisterOfferSearchIndex(CatalogItem item, CatalogPageType pageType) {
        if (item == null) return;

        CatalogManager catalogManager = currentCatalogManager();
        int searchOfferId = item.getSearchOfferId();

        if (searchOfferId != -1) {
            if (pageType == CatalogPageType.BUILDER) {
                catalogManager.buildersClubOfferDefs.remove(searchOfferId);
            } else {
                catalogManager.offerDefs.remove(searchOfferId);
            }

            CatalogPage page = catalogManager.getCatalogPage(item.getPageId(), pageType);
            removeOfferIdFromPage(page, searchOfferId);
        }
    }

    private static void removeOfferIdFromPage(CatalogPage page, int offerId) {
        if (page == null || offerId < 0) return;

        for (int i = 0; i < page.getOfferIds().size(); i++) {
            if (page.getOfferIds().getInt(i) == offerId) {
                page.getOfferIds().removeInt(i);
                return;
            }
        }
    }

    private static void attachItemToPage(CatalogItem item, CatalogPageType pageType) {
        CatalogManager catalogManager = currentCatalogManager();
        CatalogPage page = catalogManager.getCatalogPage(item.getPageId(), pageType);

        if (page == null) return;

        page.getCatalogItems().put(item.getId(), item);
    }

    private static void registerOfferSearchIndex(CatalogItem item, CatalogPageType pageType) {
        CatalogManager catalogManager = currentCatalogManager();
        CatalogPage page = catalogManager.getCatalogPage(item.getPageId(), pageType);

        if (page == null) return;

        int searchOfferId = item.getSearchOfferId();
        if (searchOfferId != -1) {
            page.addOfferId(searchOfferId);

            if (pageType == CatalogPageType.BUILDER) {
                catalogManager.buildersClubOfferDefs.put(searchOfferId, item.getId());
            } else {
                catalogManager.offerDefs.put(searchOfferId, item.getId());
            }
        }
    }
}
