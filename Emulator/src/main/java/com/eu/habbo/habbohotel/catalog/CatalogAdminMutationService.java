package com.eu.habbo.habbohotel.catalog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CatalogAdminMutationService {
    private static final int MAX_ORDER_NUMBER = 1_000_000;

    public record MutationResult(boolean success, String message) {}

    public record OfferOrder(int offerId, int orderNumber) {}

    private CatalogAdminMutationService() {
    }

    public static MutationResult setPageEnabled(int pageId, boolean enabled, CatalogPageType pageType) {
        return setPageState(pageId, enabled, pageType, "enabled", "Page enabled state saved");
    }

    public static MutationResult setPageVisible(int pageId, boolean visible, CatalogPageType pageType) {
        return setPageState(pageId, visible, pageType, "visible", "Page visibility saved");
    }

    private static MutationResult setPageState(
            int pageId, boolean state, CatalogPageType pageType, String column, String successMessage) {
        CatalogManager catalogManager = CatalogAdminCacheSync.currentCatalogManager();
        CatalogPage page = catalogManager.getCatalogPage(pageId, pageType);
        if (page == null) return new MutationResult(false, "Page not found: " + pageId);

        String tableName = pageType == CatalogPageType.BUILDER ? "catalog_pages_bc" : "catalog_pages";
        try (Connection connection = CatalogAdminCacheSync.openCatalogConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + tableName + " SET " + column + " = ? WHERE id = ?")) {
            statement.setString(1, state ? "1" : "0");
            statement.setInt(2, pageId);
            if (statement.executeUpdate() == 0) return new MutationResult(false, "Page not found: " + pageId);
        } catch (SQLException exception) {
            return new MutationResult(false, "Failed to save page state");
        }

        if ("enabled".equals(column)) page.setEnabled(state);
        else page.setVisible(state);
        return new MutationResult(true, successMessage);
    }

    public static MutationResult reorderOffers(List<OfferOrder> orders, CatalogPageType pageType) {
        if (orders == null || orders.isEmpty()) return new MutationResult(false, "Invalid reorder batch size");

        Set<Integer> uniqueOfferIds = new HashSet<>(orders.size());
        CatalogManager catalogManager = CatalogAdminCacheSync.currentCatalogManager();
        for (OfferOrder order : orders) {
            if (order.offerId() <= 0
                    || order.orderNumber() < 0
                    || order.orderNumber() > MAX_ORDER_NUMBER
                    || !uniqueOfferIds.add(order.offerId())) {
                return new MutationResult(false, "Invalid offer reorder entry");
            }
            if (catalogManager.getCatalogItem(order.offerId(), pageType) == null) {
                return new MutationResult(false, "Offer not found: " + order.offerId());
            }
        }

        String tableName = pageType == CatalogPageType.BUILDER ? "catalog_items_bc" : "catalog_items";
        try (Connection connection = CatalogAdminCacheSync.openCatalogConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + tableName + " SET order_number = ? WHERE id = ?")) {
                for (OfferOrder order : orders) {
                    statement.setInt(1, order.orderNumber());
                    statement.setInt(2, order.offerId());
                    if (statement.executeUpdate() != 1) throw new SQLException("Offer disappeared during reorder");
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                return new MutationResult(false, "Failed to reorder offers");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            return new MutationResult(false, "Failed to reorder offers");
        }

        for (OfferOrder order : orders) {
            CatalogItem item = catalogManager.getCatalogItem(order.offerId(), pageType);
            if (item != null) item.setOrderNumber(order.orderNumber());
        }
        return new MutationResult(true, "Offers reordered");
    }
}
