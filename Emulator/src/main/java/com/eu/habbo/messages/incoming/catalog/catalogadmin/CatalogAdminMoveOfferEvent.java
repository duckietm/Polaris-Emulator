package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class CatalogAdminMoveOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int offerId = this.packet.readInt();
        int orderNumber = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (offerId <= 0) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer id"));
            return;
        }

        if (orderNumber < 0) orderNumber = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement((pageType == CatalogPageType.BUILDER) ? "UPDATE catalog_items_bc SET order_number = ? WHERE id = ?" : "UPDATE catalog_items SET order_number = ? WHERE id = ?")) {
            statement.setInt(1, orderNumber);
            statement.setInt(2, offerId);
            if (statement.executeUpdate() == 0) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Offer not found: " + offerId));
                return;
            }
        }

        this.client.sendResponse(new CatalogAdminResultComposer(true, "Offer reordered"));
    }
}
