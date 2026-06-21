package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
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

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement((pageType == CatalogPageType.BUILDER) ? "DELETE FROM catalog_items_bc WHERE id = ?" : "DELETE FROM catalog_items WHERE id = ?")) {
            statement.setInt(1, offerId);
            if (statement.executeUpdate() == 0) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Offer not found: " + offerId));
                return;
            }
        }

        this.client.sendResponse(new CatalogAdminResultComposer(true, "Offer deleted"));
    }
}
