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
                ? "SELECT id, page_id, item_ids, catalog_name, 0 AS cost_credits, 0 AS cost_points, 0 AS points_type, "
                    + "1 AS amount, '0' AS club_only, extradata, '1' AS have_offer, 0 AS offer_id, "
                    + "0 AS limited_stack, 0 AS limited_sells, order_number FROM catalog_items_bc WHERE id = ? LIMIT 1"
                : "SELECT id, page_id, item_ids, catalog_name, cost_credits, cost_points, points_type, amount, "
                    + "club_only, extradata, have_offer, offer_id, limited_stack, limited_sells, order_number "
                    + "FROM catalog_items WHERE id = ? LIMIT 1";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, offerId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    this.client.sendResponse(new CatalogAdminResultComposer(false, "Offer not found: " + offerId));
                    return;
                }

                this.client.sendResponse(new CatalogAdminOfferDetailsComposer(
                        set.getInt("id"),
                        set.getInt("page_id"),
                        set.getString("item_ids"),
                        set.getString("catalog_name"),
                        set.getInt("cost_credits"),
                        set.getInt("cost_points"),
                        set.getInt("points_type"),
                        set.getInt("amount"),
                        set.getBoolean("club_only"),
                        set.getString("extradata"),
                        set.getBoolean("have_offer"),
                        set.getInt("offer_id"),
                        set.getInt("limited_stack"),
                        set.getInt("limited_sells"),
                        set.getInt("order_number"),
                        pageType.name()
                ));
            }
        }
    }
}
