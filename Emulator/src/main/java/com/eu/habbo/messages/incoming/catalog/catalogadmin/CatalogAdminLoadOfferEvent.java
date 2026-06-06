package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminOfferDetailsComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CatalogAdminLoadOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int offerId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        String sql = (pageType == CatalogPageType.BUILDER)
                ? "SELECT id, order_number FROM catalog_items_bc WHERE id = ? LIMIT 1"
                : "SELECT id, offer_id, limited_stack, order_number FROM catalog_items WHERE id = ? LIMIT 1";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offerId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return;

                if (pageType == CatalogPageType.BUILDER) {
                    this.client.sendResponse(new CatalogAdminOfferDetailsComposer(
                            set.getInt("id"),
                            0,
                            0,
                            set.getInt("order_number")
                    ));
                } else {
                    this.client.sendResponse(new CatalogAdminOfferDetailsComposer(
                            set.getInt("id"),
                            set.getInt("offer_id"),
                            set.getInt("limited_stack"),
                            set.getInt("order_number")
                    ));
                }
            }
        }
    }
}
