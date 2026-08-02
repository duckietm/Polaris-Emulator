package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogAdminCacheSync;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class CatalogAdminDeleteOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int offerId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (offerId <= 0) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer id"));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (pageType != CatalogPageType.BUILDER) {
                    try (PreparedStatement sold = connection.prepareStatement(
                            "SELECT 1 FROM catalog_items_limited WHERE catalog_item_id = ? AND user_id <> 0 LIMIT 1")) {
                        sold.setInt(1, offerId);
                        try (var set = sold.executeQuery()) {
                            if (set.next()) {
                                connection.rollback();
                                this.client.sendResponse(new CatalogAdminResultComposer(
                                        false, "Cannot delete a limited offer after items have been sold"));
                                return;
                            }
                        }
                    }

                    try (PreparedStatement limited = connection.prepareStatement(
                            "DELETE FROM catalog_items_limited WHERE catalog_item_id = ?")) {
                        limited.setInt(1, offerId);
                        limited.executeUpdate();
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        (pageType == CatalogPageType.BUILDER)
                                ? "DELETE FROM catalog_items_bc WHERE id = ?"
                                : "DELETE FROM catalog_items WHERE id = ?")) {
                    statement.setInt(1, offerId);
                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        this.client.sendResponse(new CatalogAdminResultComposer(false, "Offer not found: " + offerId));
                        return;
                    }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }

        CatalogAdminCacheSync.removeCatalogItem(offerId, pageType, -1);
        this.client.sendResponse(new CatalogAdminResultComposer(true, "Offer deleted"));
    }
}
